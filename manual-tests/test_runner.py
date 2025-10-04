#!/usr/bin/env python3
"""
E-Commerce Platform Interactive Testing Tool
A beautiful CLI application for testing the e-commerce platform end-to-end with Docker container management.
"""

import json
import os
import subprocess
import time
import uuid
import signal
import sys
from datetime import datetime
from typing import Optional, Dict, Any, List, Tuple
from pathlib import Path

import requests
import psycopg2
import redis
from kafka import KafkaConsumer
from rich.console import Console
from rich.table import Table
from rich.panel import Panel
from rich.progress import Progress, SpinnerColumn, TextColumn, BarColumn, TimeElapsedColumn
from rich.tree import Tree
from rich.live import Live
from rich.layout import Layout
from rich.prompt import Prompt, Confirm
from rich import box
from rich.syntax import Syntax
from rich.markdown import Markdown
import typer

app = typer.Typer(help="E-Commerce Platform Interactive Testing Tool with Docker Management", invoke_without_command=True, no_args_is_help=False)
console = Console()

# Configuration
CUSTOMER_SERVICE_URL = "http://localhost:8080"
ORDER_SERVICE_URL = "http://localhost:8081"
POSTGRES_CUSTOMER_CONFIG = {
    "host": "localhost",
    "port": 5432,
    "database": "customer_db",
    "user": "customer_user",
    "password": "customer_pass"
}
POSTGRES_ORDER_CONFIG = {
    "host": "localhost",
    "port": 5433,
    "database": "order_db",
    "user": "order_user",
    "password": "order_pass"
}
REDIS_CONFIG = {
    "host": "localhost",
    "port": 6379,
    "decode_responses": True
}
KAFKA_CONFIG = {
    "bootstrap_servers": ["localhost:9092"],
    "auto_offset_reset": "latest",
    "consumer_timeout_ms": 5000
}

# Docker configuration
DOCKER_COMPOSE_FILE = "../infrastructure/docker-compose-full.yml"
DOCKER_PROJECT_NAME = "ecommerce-platform"

# Global state
containers_managed = False
cleanup_on_exit = True


class TestContext:
    """Holds test execution context and results"""
    def __init__(self):
        self.manager_token: Optional[str] = None
        self.category_id: Optional[str] = None
        self.product_ids: List[str] = []
        self.session_id: str = f"test-session-{uuid.uuid4()}"
        self.cart_item_ids: List[str] = []
        self.order_number: Optional[str] = None
        self.results: List[Dict[str, Any]] = []
        self.test_data = {
            "categories": [],
            "products": [],
            "orders": []
        }


ctx = TestContext()


def signal_handler(sig, frame):
    """Handle Ctrl+C gracefully"""
    console.print("\n\n[yellow]⚠️  Interrupted by user[/yellow]")
    if containers_managed and cleanup_on_exit:
        console.print("[cyan]Cleaning up containers...[/cyan]")
        stop_containers()
    sys.exit(0)


signal.signal(signal.SIGINT, signal_handler)


def print_header(title: str, subtitle: str = ""):
    """Print a beautiful section header"""
    console.print()
    if subtitle:
        console.print(Panel(
            f"[bold cyan]{title}[/bold cyan]\n[dim]{subtitle}[/dim]",
            box=box.DOUBLE
        ))
    else:
        console.print(Panel(f"[bold cyan]{title}[/bold cyan]", box=box.DOUBLE))
    console.print()


def print_step(step: str, status: str = "info"):
    """Print a test step"""
    emoji = {
        "info": "🔍",
        "success": "✅",
        "error": "❌",
        "warning": "⚠️",
        "running": "⏳"
    }.get(status, "ℹ️")
    color = {
        "info": "cyan",
        "success": "green",
        "error": "red",
        "warning": "yellow",
        "running": "blue"
    }.get(status, "white")
    console.print(f"{emoji} [{color}]{step}[/{color}]")


def print_result(name: str, value: Any, success: bool = True, indent: int = 1):
    """Print a test result"""
    color = "green" if success else "red"
    prefix = "  " * indent + "└─"
    console.print(f"{prefix} [bold]{name}:[/bold] [{color}]{value}[/{color}]")


def run_command(cmd: List[str], cwd: str = None, capture: bool = True) -> Tuple[int, str, str]:
    """Run a shell command and return exit code, stdout, stderr"""
    try:
        if capture:
            result = subprocess.run(
                cmd,
                cwd=cwd,
                capture_output=True,
                text=True,
                timeout=300
            )
            return result.returncode, result.stdout, result.stderr
        else:
            result = subprocess.run(cmd, cwd=cwd, timeout=300)
            return result.returncode, "", ""
    except subprocess.TimeoutExpired:
        return -1, "", "Command timed out"
    except Exception as e:
        return -1, "", str(e)


def start_containers(detach: bool = True):
    """Start Docker containers"""
    global containers_managed, cleanup_on_exit

    print_header("🐳 Starting Docker Containers", "Launching infrastructure and services")

    infrastructure_dir = Path(__file__).parent.parent / "infrastructure"

    if not infrastructure_dir.exists():
        print_step("Infrastructure directory not found", "error")
        raise typer.Exit(1)

    print_step("Checking Docker daemon...")
    code, _, _ = run_command(["docker", "info"])
    if code != 0:
        print_step("Docker daemon is not running. Please start Docker.", "error")
        raise typer.Exit(1)
    print_step("Docker daemon is running", "success")

    # Clean up any existing containers with conflicting names
    print_step("Cleaning up existing containers...")
    cleanup_cmd = [
        "docker-compose",
        "-f", "docker-compose-full.yml",
        "-p", DOCKER_PROJECT_NAME,
        "down"
    ]
    run_command(cleanup_cmd, cwd=str(infrastructure_dir))

    # Remove any orphaned containers
    orphan_cleanup_cmd = ["docker", "ps", "-a", "-q", "--filter", f"name={DOCKER_PROJECT_NAME}"]
    code, stdout, _ = run_command(orphan_cleanup_cmd)
    if code == 0 and stdout.strip():
        container_ids = stdout.strip().split('\n')
        for container_id in container_ids:
            run_command(["docker", "rm", "-f", container_id])

    print_step("Starting containers with docker-compose...")

    cmd = [
        "docker-compose",
        "-f", "docker-compose-full.yml",
        "-p", DOCKER_PROJECT_NAME,
        "up", "-d", "--build"
    ]

    with Progress(
        SpinnerColumn(),
        TextColumn("[progress.description]{task.description}"),
        BarColumn(),
        TimeElapsedColumn(),
        console=console
    ) as progress:
        task = progress.add_task("Building and starting containers...", total=None)
        code, stdout, stderr = run_command(cmd, cwd=str(infrastructure_dir))

    if code != 0:
        # Check if it's a container name conflict
        if "is already in use" in stderr:
            print_step("Container name conflict detected. Attempting cleanup and retry...", "warning")

            # Force cleanup
            run_command(cleanup_cmd, cwd=str(infrastructure_dir))

            # Try to remove specific conflicting containers
            for container_name in ["redpanda", "postgres-order", "postgres-customer", "redis"]:
                run_command(["docker", "rm", "-f", container_name])

            # Retry
            code, stdout, stderr = run_command(cmd, cwd=str(infrastructure_dir))

            if code != 0:
                print_step(f"Failed to start containers after cleanup: {stderr}", "error")
                raise typer.Exit(1)
        else:
            print_step(f"Failed to start containers: {stderr}", "error")
            raise typer.Exit(1)

    print_step("Containers started successfully", "success")
    containers_managed = True
    cleanup_on_exit = not detach

    # Wait for services to be healthy
    print_step("Waiting for services to be healthy...")
    wait_for_services()


def stop_containers():
    """Stop Docker containers"""
    print_header("🛑 Stopping Docker Containers")
    
    infrastructure_dir = Path(__file__).parent.parent / "infrastructure"
    
    print_step("Stopping containers...")
    cmd = [
        "docker-compose",
        "-f", "docker-compose-full.yml",
        "-p", DOCKER_PROJECT_NAME,
        "down"
    ]
    
    code, stdout, stderr = run_command(cmd, cwd=str(infrastructure_dir))
    
    if code == 0:
        print_step("Containers stopped successfully", "success")
    else:
        print_step(f"Failed to stop containers: {stderr}", "error")


def check_containers_status() -> bool:
    """Check if containers are running"""
    code, stdout, _ = run_command([
        "docker-compose",
        "-p", DOCKER_PROJECT_NAME,
        "ps", "-q"
    ])
    return code == 0 and bool(stdout.strip())


def wait_for_services(max_wait: int = 120):
    """Wait for services to be ready"""
    services = [
        (CUSTOMER_SERVICE_URL, "Customer Service"),
        (ORDER_SERVICE_URL, "Order Service")
    ]
    
    start_time = time.time()
    
    with Progress(
        SpinnerColumn(),
        TextColumn("[progress.description]{task.description}"),
        TimeElapsedColumn(),
        console=console
    ) as progress:
        for url, name in services:
            task = progress.add_task(f"Waiting for {name}...", total=None)
            
            while time.time() - start_time < max_wait:
                try:
                    response = requests.get(f"{url}/actuator/health", timeout=2)
                    if response.status_code == 200:
                        data = response.json()
                        if data.get("status") == "UP":
                            progress.update(task, description=f"{name} is ready ✓")
                            print_step(f"{name} is healthy", "success")
                            break
                except:
                    pass
                time.sleep(2)
            else:
                print_step(f"{name} did not become healthy in time", "warning")


def api_call(method: str, url: str, expected_status: int = None, **kwargs) -> Tuple[requests.Response, bool]:
    """Make an API call with error handling"""
    try:
        response = requests.request(method, url, **kwargs, timeout=10)
        if expected_status is not None:
            success = response.status_code == expected_status
        else:
            success = 200 <= response.status_code < 300
        return response, success
    except Exception as e:
        print_step(f"API call failed: {e}", "error")
        raise


def show_response(response: requests.Response, success: bool = True):
    """Display API response in a beautiful format"""
    status_color = "green" if success else "red"
    console.print(f"  [bold]Status Code:[/bold] [{status_color}]{response.status_code}[/{status_color}]")
    
    try:
        data = response.json()
        json_str = json.dumps(data, indent=2)
        syntax = Syntax(json_str, "json", theme="monokai", line_numbers=False)
        console.print(Panel(syntax, title="Response", border_style=status_color))
    except:
        console.print(f"  [dim]{response.text[:500]}[/dim]")


@app.command()
def start(
    interactive: bool = typer.Option(True, "--interactive/--no-interactive", "-i/-ni", help="Run in interactive mode"),
    keep_alive: bool = typer.Option(False, "--keep-alive", "-k", help="Keep containers running after tests"),
    detach: bool = typer.Option(False, "--detach", "-d", help="Start containers and exit (persistent)"),
):
    """Start the platform and run interactive tests"""
    global cleanup_on_exit
    
    print_header(
        "🚀 E-Commerce Platform Interactive Testing",
        "Complete end-to-end testing with Docker container management"
    )
    
    # Start containers
    start_containers(detach=detach)
    
    if detach:
        console.print()
        console.print(Panel(
            "[bold green]✓ Containers are running in detached mode[/bold green]\n\n"
            f"Access services at:\n"
            f"  • Customer Service: {CUSTOMER_SERVICE_URL}\n"
            f"  • Order Service: {ORDER_SERVICE_URL}\n\n"
            f"Run tests with: [cyan]python test_runner.py interactive[/cyan]\n"
            f"Stop containers with: [cyan]python test_runner.py stop[/cyan]",
            title="Ready",
            box=box.DOUBLE
        ))
        return
    
    cleanup_on_exit = not keep_alive
    
    if interactive:
        interactive_mode()
    else:
        # Run full flow automatically
        full_flow()
    
    # Cleanup
    if cleanup_on_exit:
        console.print()
        if Confirm.ask("Stop and remove containers?", default=True):
            stop_containers()
    else:
        console.print()
        console.print("[yellow]ℹ️  Containers are still running. Use 'python test_runner.py stop' to stop them.[/yellow]")


@app.command()
def stop():
    """Stop all Docker containers"""
    stop_containers()


@app.command()
def interactive():
    """Run interactive testing mode (requires containers to be running)"""
    print_header("🎮 Interactive Testing Mode")
    
    # Check if containers are running
    if not check_containers_status():
        console.print("[yellow]⚠️  Containers are not running.[/yellow]")
        if Confirm.ask("Start containers now?", default=True):
            start_containers(detach=False)
        else:
            raise typer.Exit(0)
    
    interactive_mode()


def interactive_mode():
    """Interactive testing mode with menu"""
    scenarios = [
        ("1", "Happy Path - Complete Flow", "Run successful end-to-end journey"),
        ("2", "Test Individual Endpoints", "Test specific endpoints interactively"),
        ("3", "Error Scenarios - 400 Bad Request", "Test validation and error handling"),
        ("4", "Error Scenarios - 404 Not Found", "Test resource not found scenarios"),
        ("5", "Error Scenarios - Cart & Checkout", "Test cart and checkout error cases"),
        ("6", "Database Verification", "Inspect database state"),
        ("7", "Kafka Verification", "Check event messages"),
        ("8", "Health Checks", "Verify service health"),
        ("q", "Quit", "Exit interactive mode"),
    ]
    
    while True:
        console.print()
        console.print(Panel(
            "[bold cyan]Interactive Testing Menu[/bold cyan]",
            box=box.DOUBLE
        ))
        console.print()
        
        table = Table(box=box.ROUNDED, show_header=False)
        table.add_column("Option", style="cyan", width=5)
        table.add_column("Test Scenario", style="green")
        table.add_column("Description", style="dim")
        
        for opt, name, desc in scenarios:
            table.add_row(opt, name, desc)
        
        console.print(table)
        console.print()
        
        choice = Prompt.ask("Select a test scenario", choices=[s[0] for s in scenarios], default="1")
        
        if choice == "q":
            break
        elif choice == "1":
            happy_path_flow()
        elif choice == "2":
            test_individual_endpoints()
        elif choice == "3":
            error_scenarios_400()
        elif choice == "4":
            error_scenarios_404()
        elif choice == "5":
            error_scenarios_cart_checkout()
        elif choice == "6":
            verify_database()
        elif choice == "7":
            verify_kafka()
        elif choice == "8":
            test_health()
        
        console.print()
        if not Confirm.ask("Continue testing?", default=True):
            break
    
    print_summary()


def happy_path_flow():
    """Run complete happy path flow"""
    print_header("✨ Happy Path - Complete Flow")
    
    ctx.results.clear()
    ctx.session_id = f"test-session-{uuid.uuid4()}"
    
    test_health()
    test_auth()
    test_catalog_management()
    test_cart_operations()
    test_checkout()
    test_order_processing()


def test_individual_endpoints():
    """Test individual endpoints interactively"""
    print_header("🔧 Individual Endpoint Testing")
    
    endpoints = [
        ("1", "GET /api/v1/categories", "List all categories"),
        ("2", "POST /api/v1/categories", "Create a category"),
        ("3", "GET /api/v1/products", "List all products"),
        ("4", "POST /api/v1/products", "Create a product"),
        ("5", "GET /api/v1/carts/{sessionId}", "Get cart"),
        ("6", "POST /api/v1/carts/{sessionId}/items", "Add item to cart"),
        ("7", "POST /api/v1/checkout", "Checkout"),
        ("8", "GET /api/v1/orders/{orderNumber}", "Get order"),
        ("b", "Back", "Return to main menu"),
    ]
    
    table = Table(box=box.ROUNDED)
    table.add_column("Option", style="cyan")
    table.add_column("Endpoint", style="green")
    table.add_column("Description", style="dim")
    
    for opt, endpoint, desc in endpoints:
        table.add_row(opt, endpoint, desc)
    
    console.print(table)
    console.print()
    
    choice = Prompt.ask("Select an endpoint", choices=[e[0] for e in endpoints])
    
    if choice == "b":
        return
    elif choice == "1":
        test_get_categories()
    elif choice == "2":
        test_create_category_interactive()
    elif choice == "3":
        test_get_products()
    elif choice == "4":
        test_create_product_interactive()
    elif choice == "5":
        test_get_cart_interactive()
    elif choice == "6":
        test_add_to_cart_interactive()
    elif choice == "7":
        test_checkout_interactive()
    elif choice == "8":
        test_get_order_interactive()


def error_scenarios_400():
    """Test 400 Bad Request scenarios"""
    print_header("❌ Error Scenarios - 400 Bad Request", "Testing validation and error handling")
    
    console.print("[bold]Testing invalid requests...[/bold]\n")
    
    # Test 1: Create category with missing name
    print_step("Test: Create category with missing name")
    response, _ = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/categories",
        expected_status=400,
        json={"description": "Missing name field"}
    )
    show_response(response, success=(response.status_code == 400))
    ctx.results.append({"test": "validation_missing_name", "status": "success" if response.status_code == 400 else "failed"})
    
    # Test 2: Create product with invalid price
    print_step("Test: Create product with negative price")
    response, _ = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/products",
        expected_status=400,
        json={
            "sku": "TEST-SKU",
            "name": "Test Product",
            "price": -10.00,  # Invalid negative price
            "inventoryQuantity": 10,
            "categoryId": "some-id"
        }
    )
    show_response(response, success=(response.status_code == 400))
    ctx.results.append({"test": "validation_negative_price", "status": "success" if response.status_code == 400 else "failed"})
    
    # Test 3: Invalid email in checkout
    print_step("Test: Checkout with invalid email")
    response, _ = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/checkout",
        expected_status=400,
        json={
            "sessionId": "test-session",
            "customerInfo": {
                "name": "Test User",
                "email": "not-an-email",  # Invalid email
                "phone": "+14155551234",
                "shippingAddress": {
                    "street": "123 Main St",
                    "city": "San Francisco",
                    "state": "CA",
                    "postalCode": "94105",
                    "country": "USA"
                }
            }
        }
    )
    show_response(response, success=(response.status_code == 400))
    ctx.results.append({"test": "validation_invalid_email", "status": "success" if response.status_code == 400 else "failed"})
    
    # Test 4: Add to cart with invalid quantity
    print_step("Test: Add to cart with zero quantity")
    response, _ = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/carts/test-session/items",
        expected_status=400,
        json={"productId": "some-id", "quantity": 0}  # Invalid quantity
    )
    show_response(response, success=(response.status_code == 400))
    ctx.results.append({"test": "validation_zero_quantity", "status": "success" if response.status_code == 400 else "failed"})


def error_scenarios_404():
    """Test 404 Not Found scenarios"""
    print_header("🔍 Error Scenarios - 404 Not Found", "Testing non-existent resources")
    
    console.print("[bold]Testing resource not found...[/bold]\n")
    
    # Test 1: Get non-existent category
    fake_id = str(uuid.uuid4())
    print_step(f"Test: Get non-existent category (ID: {fake_id})")
    response, _ = api_call(
        "GET",
        f"{CUSTOMER_SERVICE_URL}/api/v1/categories/{fake_id}",
        expected_status=404
    )
    show_response(response, success=(response.status_code == 404))
    ctx.results.append({"test": "404_category", "status": "success" if response.status_code == 404 else "failed"})
    
    # Test 2: Get non-existent product
    fake_id = str(uuid.uuid4())
    print_step(f"Test: Get non-existent product (ID: {fake_id})")
    response, _ = api_call(
        "GET",
        f"{CUSTOMER_SERVICE_URL}/api/v1/products/{fake_id}",
        expected_status=404
    )
    show_response(response, success=(response.status_code == 404))
    ctx.results.append({"test": "404_product", "status": "success" if response.status_code == 404 else "failed"})
    
    # Test 3: Get non-existent order
    print_step("Test: Get non-existent order")
    response, _ = api_call(
        "GET",
        f"{ORDER_SERVICE_URL}/api/v1/orders/ORD-99999999-999",
        expected_status=404
    )
    show_response(response, success=(response.status_code == 404))
    ctx.results.append({"test": "404_order", "status": "success" if response.status_code == 404 else "failed"})
    
    # Test 4: Update non-existent category
    fake_id = str(uuid.uuid4())
    print_step(f"Test: Update non-existent category (ID: {fake_id})")
    response, _ = api_call(
        "PUT",
        f"{CUSTOMER_SERVICE_URL}/api/v1/categories/{fake_id}",
        expected_status=404,
        json={"name": "Updated Name", "description": "Updated Description"}
    )
    show_response(response, success=(response.status_code == 404))
    ctx.results.append({"test": "404_update_category", "status": "success" if response.status_code == 404 else "failed"})


def error_scenarios_cart_checkout():
    """Test cart and checkout error scenarios"""
    print_header("🛒 Error Scenarios - Cart & Checkout", "Testing cart and checkout validation")
    
    console.print("[bold]Testing cart and checkout errors...[/bold]\n")
    
    # Test 1: Checkout with empty cart
    empty_session = f"empty-{uuid.uuid4()}"
    print_step(f"Test: Checkout with empty cart (session: {empty_session})")
    response, _ = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/checkout",
        expected_status=400,
        json={
            "sessionId": empty_session,
            "customerInfo": {
                "name": "Test User",
                "email": "test@example.com",
                "phone": "+14155551234",
                "shippingAddress": {
                    "street": "123 Main St",
                    "city": "San Francisco",
                    "state": "CA",
                    "postalCode": "94105",
                    "country": "USA"
                }
            }
        }
    )
    show_response(response, success=(response.status_code in [400, 404]))
    ctx.results.append({"test": "empty_cart_checkout", "status": "success" if response.status_code in [400, 404] else "failed"})
    
    # Test 2: Add non-existent product to cart
    fake_product_id = str(uuid.uuid4())
    print_step(f"Test: Add non-existent product to cart (ID: {fake_product_id})")
    response, _ = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/carts/test-session/items",
        expected_status=404,
        json={"productId": fake_product_id, "quantity": 1}
    )
    show_response(response, success=(response.status_code == 404))
    ctx.results.append({"test": "add_nonexistent_product", "status": "success" if response.status_code == 404 else "failed"})
    
    # Test 3: Checkout with incomplete address
    print_step("Test: Checkout with incomplete address")
    response, _ = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/checkout",
        expected_status=400,
        json={
            "sessionId": "test-session",
            "customerInfo": {
                "name": "Test User",
                "email": "test@example.com",
                "phone": "+14155551234",
                "shippingAddress": {
                    "city": "San Francisco"
                    # Missing required fields
                }
            }
        }
    )
    show_response(response, success=(response.status_code == 400))
    ctx.results.append({"test": "incomplete_address", "status": "success" if response.status_code == 400 else "failed"})


# Helper functions for interactive endpoint testing
def test_get_categories():
    print_step("GET /api/v1/categories")
    response, success = api_call("GET", f"{CUSTOMER_SERVICE_URL}/api/v1/categories")
    show_response(response, success)


def test_create_category_interactive():
    print_step("POST /api/v1/categories")
    name = Prompt.ask("Category name", default="Test Category")
    description = Prompt.ask("Category description", default="A test category")
    
    response, success = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/categories",
        json={"name": name, "description": description}
    )
    show_response(response, success)
    
    if success:
        data = response.json()
        ctx.category_id = data.get("id")
        console.print(f"[green]✓ Category created with ID: {ctx.category_id}[/green]")


def test_get_products():
    print_step("GET /api/v1/products")
    response, success = api_call("GET", f"{CUSTOMER_SERVICE_URL}/api/v1/products")
    show_response(response, success)


def test_create_product_interactive():
    if not ctx.category_id:
        console.print("[yellow]⚠️  No category selected. Creating one first...[/yellow]")
        test_create_category_interactive()
    
    print_step("POST /api/v1/products")
    name = Prompt.ask("Product name", default="Test Product")
    sku = Prompt.ask("SKU", default=f"SKU-{uuid.uuid4().hex[:6].upper()}")
    price = float(Prompt.ask("Price", default="29.99"))
    quantity = int(Prompt.ask("Inventory quantity", default="100"))
    
    response, success = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/products",
        json={
            "sku": sku,
            "name": name,
            "description": "A test product",
            "price": price,
            "inventoryQuantity": quantity,
            "categoryId": ctx.category_id
        }
    )
    show_response(response, success)
    
    if success:
        data = response.json()
        product_id = data.get("id")
        ctx.product_ids.append(product_id)
        console.print(f"[green]✓ Product created with ID: {product_id}[/green]")


def test_get_cart_interactive():
    session_id = Prompt.ask("Session ID", default=ctx.session_id)
    print_step(f"GET /api/v1/carts/{session_id}")
    response, success = api_call("GET", f"{CUSTOMER_SERVICE_URL}/api/v1/carts/{session_id}")
    show_response(response, success)


def test_add_to_cart_interactive():
    if not ctx.product_ids:
        console.print("[yellow]⚠️  No products available. Create a product first.[/yellow]")
        return
    
    session_id = Prompt.ask("Session ID", default=ctx.session_id)
    console.print(f"[dim]Available products: {', '.join(ctx.product_ids[:5])}[/dim]")
    product_id = Prompt.ask("Product ID", default=ctx.product_ids[0] if ctx.product_ids else "")
    quantity = int(Prompt.ask("Quantity", default="1"))
    
    print_step(f"POST /api/v1/carts/{session_id}/items")
    response, success = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/carts/{session_id}/items",
        json={"productId": product_id, "quantity": quantity}
    )
    show_response(response, success)


def test_checkout_interactive():
    session_id = Prompt.ask("Session ID", default=ctx.session_id)
    name = Prompt.ask("Customer name", default="John Doe")
    email = Prompt.ask("Customer email", default="john.doe@example.com")
    
    print_step("POST /api/v1/checkout")
    response, success = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/checkout",
        json={
            "sessionId": session_id,
            "customerInfo": {
                "name": name,
                "email": email,
                "phone": "+14155551234",
                "shippingAddress": {
                    "street": "123 Main Street",
                    "city": "San Francisco",
                    "state": "CA",
                    "postalCode": "94105",
                    "country": "USA"
                }
            }
        }
    )
    show_response(response, success)
    
    if success:
        data = response.json()
        ctx.order_number = data.get("orderNumber")
        console.print(f"[green]✓ Order created: {ctx.order_number}[/green]")


def test_get_order_interactive():
    order_number = Prompt.ask("Order number", default=ctx.order_number or "ORD-20240101-001")
    print_step(f"GET /api/v1/orders/{order_number}")
    response, success = api_call("GET", f"{ORDER_SERVICE_URL}/api/v1/orders/{order_number}")
    show_response(response, success)


@app.command()
def full_flow():
    """Run complete end-to-end test flow (automated)"""
    print_header("🚀 E-Commerce Platform End-to-End Test")
    
    console.print("[bold yellow]This will test the complete customer journey:[/bold yellow]")
    console.print("  1. Health checks")
    console.print("  2. Manager authentication")
    console.print("  3. Category & Product creation")
    console.print("  4. Shopping cart operations")
    console.print("  5. Checkout process")
    console.print("  6. Order processing & payment")
    console.print("  7. Database & Kafka verification")
    console.print()
    
    time.sleep(2)
    
    ctx.results.clear()
    ctx.session_id = f"test-session-{uuid.uuid4()}"
    
    # Run all tests in sequence
    test_health()
    test_auth()
    test_catalog_management()
    test_cart_operations()
    test_checkout()
    test_order_processing()
    verify_database()
    verify_kafka()
    
    # Print summary
    print_summary()


@app.command()
def test_health():
    """Test health endpoints"""
    print_header("🏥 Health Check")
    
    services = [
        ("Customer Service", f"{CUSTOMER_SERVICE_URL}/actuator/health"),
        ("Order Service", f"{ORDER_SERVICE_URL}/actuator/health")
    ]
    
    for name, url in services:
        print_step(f"Checking {name}...")
        try:
            response, _ = api_call("GET", url)
            if response.status_code == 200:
                data = response.json()
                status = data.get("status", "UNKNOWN")
                print_result("Status", status, status == "UP")
                
                # Check components
                components = data.get("components", {})
                for component, details in components.items():
                    comp_status = details.get("status", "UNKNOWN")
                    print_result(f"{component}", comp_status, comp_status == "UP", indent=2)
                
                ctx.results.append({"test": f"health_{name.lower().replace(' ', '_')}", "status": "success"})
            else:
                print_step(f"Health check failed: {response.status_code}", "error")
                ctx.results.append({"test": f"health_{name.lower().replace(' ', '_')}", "status": "failed"})
        except Exception as e:
            print_step(f"Health check failed: {e}", "error")
            ctx.results.append({"test": f"health_{name.lower().replace(' ', '_')}", "status": "failed"})


@app.command()
def test_auth():
    """Test authentication and obtain manager token"""
    print_header("🔐 Authentication")
    
    print_step("Logging in as manager...")
    response, _ = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/auth/login",
        json={"username": "manager", "password": "manager123"}
    )
    
    if response.status_code == 200:
        data = response.json()
        ctx.manager_token = data.get("accessToken")
        print_result("Token obtained", "✓", True)
        print_result("Token type", data.get("tokenType", "Bearer"))
        print_result("Expires in", f"{data.get('expiresIn', 0)}s")
        ctx.results.append({"test": "auth", "status": "success"})
    else:
        print_step(f"Authentication failed: {response.status_code}", "error")
        print_result("Response", response.text, False)
        ctx.results.append({"test": "auth", "status": "failed"})


@app.command()
def test_catalog_management():
    """Test catalog management (categories and products)"""
    print_header("📚 Catalog Management")
    
    if not ctx.manager_token:
        print_step("No manager token available. Running auth first...", "warning")
        test_auth()
    
    headers = {"Authorization": f"Bearer {ctx.manager_token}"}
    
    # Create category
    print_step("Creating category 'Electronics'...")
    response, _ = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/categories",
        headers=headers,
        json={
            "name": "Electronics",
            "description": "Electronic devices and accessories"
        }
    )
    
    if response.status_code == 201:
        data = response.json()
        ctx.category_id = data.get("id")
        print_result("Category ID", ctx.category_id, True)
        print_result("Category Name", data.get("name"))
        ctx.results.append({"test": "create_category", "status": "success"})
    else:
        print_step(f"Category creation failed: {response.status_code}", "error")
        ctx.results.append({"test": "create_category", "status": "failed"})
        return
    
    # Create products
    products = [
        {
            "sku": f"SKU-HEADPHONE-{uuid.uuid4().hex[:6].upper()}",
            "name": "Premium Wireless Headphones",
            "description": "Noise-cancelling Bluetooth headphones",
            "price": 149.99,
            "inventoryQuantity": 50,
            "categoryId": ctx.category_id
        },
        {
            "sku": f"SKU-CASE-{uuid.uuid4().hex[:6].upper()}",
            "name": "Protective Phone Case",
            "description": "Shockproof case with military-grade protection",
            "price": 29.99,
            "inventoryQuantity": 200,
            "categoryId": ctx.category_id
        }
    ]
    
    for product in products:
        print_step(f"Creating product '{product['name']}'...")
        response, _ = api_call(
            "POST",
            f"{CUSTOMER_SERVICE_URL}/api/v1/products",
            headers=headers,
            json=product
        )
        
        if response.status_code == 201:
            data = response.json()
            product_id = data.get("id")
            ctx.product_ids.append(product_id)
            print_result("Product ID", product_id, True)
            print_result("SKU", data.get("sku"))
            print_result("Price", f"${data.get('price')}")
            ctx.results.append({"test": f"create_product_{product['sku']}", "status": "success"})
        else:
            print_step(f"Product creation failed: {response.status_code}", "error")
            ctx.results.append({"test": f"create_product_{product['sku']}", "status": "failed"})


@app.command()
def test_cart_operations():
    """Test shopping cart operations"""
    print_header("🛒 Shopping Cart")
    
    if not ctx.product_ids:
        print_step("No products available. Running catalog creation first...", "warning")
        test_catalog_management()
    
    print_step(f"Using session ID: {ctx.session_id}")
    
    # Add items to cart
    for i, product_id in enumerate(ctx.product_ids):
        quantity = 2 if i == 0 else 1
        print_step(f"Adding product {i+1} to cart (quantity: {quantity})...")
        
        response, _ = api_call(
            "POST",
            f"{CUSTOMER_SERVICE_URL}/api/v1/carts/{ctx.session_id}/items",
            json={"productId": product_id, "quantity": quantity}
        )
        
        if response.status_code == 200:
            data = response.json()
            print_result("Cart Items", len(data.get("items", [])))
            print_result("Subtotal", f"${data.get('subtotal')}")
            ctx.results.append({"test": f"add_to_cart_{i}", "status": "success"})
        else:
            print_step(f"Failed to add item: {response.status_code}", "error")
            ctx.results.append({"test": f"add_to_cart_{i}", "status": "failed"})
    
    # Get cart
    print_step("Retrieving cart...")
    response, _ = api_call("GET", f"{CUSTOMER_SERVICE_URL}/api/v1/carts/{ctx.session_id}")
    
    if response.status_code == 200:
        data = response.json()
        print_result("Total Items", len(data.get("items", [])), True)
        print_result("Subtotal", f"${data.get('subtotal')}", True)
        
        # Display cart items
        table = Table(title="Cart Contents", box=box.ROUNDED)
        table.add_column("Product", style="cyan")
        table.add_column("SKU", style="magenta")
        table.add_column("Quantity", justify="right", style="green")
        table.add_column("Price", justify="right", style="yellow")
        table.add_column("Subtotal", justify="right", style="green")
        
        for item in data.get("items", []):
            table.add_row(
                item.get("productName", ""),
                item.get("productSku", ""),
                str(item.get("quantity", 0)),
                f"${item.get('priceSnapshot', 0):.2f}",
                f"${item.get('subtotal', 0):.2f}"
            )
        
        console.print(table)
        ctx.results.append({"test": "get_cart", "status": "success"})
    else:
        print_step(f"Failed to get cart: {response.status_code}", "error")
        ctx.results.append({"test": "get_cart", "status": "failed"})


@app.command()
def test_checkout():
    """Test checkout process"""
    print_header("💳 Checkout")
    
    if not ctx.session_id:
        print_step("No active cart. Creating cart first...", "warning")
        test_cart_operations()
    
    print_step("Processing checkout...")
    
    checkout_data = {
        "sessionId": ctx.session_id,
        "customerInfo": {
            "name": "John Doe",
            "email": "john.doe@example.com",
            "phone": "+14155551234",
            "shippingAddress": {
                "street": "123 Main Street, Apt 4B",
                "city": "San Francisco",
                "state": "CA",
                "postalCode": "94105",
                "country": "USA"
            }
        }
    }
    
    response, _ = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/checkout",
        json=checkout_data
    )
    
    if response.status_code == 201:
        data = response.json()
        ctx.order_number = data.get("orderNumber")
        print_result("Order Number", ctx.order_number, True)
        print_result("Status", data.get("status"))
        print_result("Message", data.get("message"))
        ctx.results.append({"test": "checkout", "status": "success"})
        
        console.print()
        console.print("[bold green]✨ Order created successfully![/bold green]")
        console.print(f"[yellow]Order Number: {ctx.order_number}[/yellow]")
    else:
        print_step(f"Checkout failed: {response.status_code}", "error")
        print_result("Response", response.text, False)
        ctx.results.append({"test": "checkout", "status": "failed"})


@app.command()
def test_order_processing():
    """Test order processing and payment"""
    print_header("📦 Order Processing")
    
    if not ctx.order_number:
        print_step("No order available. Running checkout first...", "warning")
        test_checkout()
    
    print_step("Waiting for order processing (5 seconds)...")
    
    with Progress(
        SpinnerColumn(),
        TextColumn("[progress.description]{task.description}"),
        console=console
    ) as progress:
        task = progress.add_task("Processing order...", total=5)
        for _ in range(5):
            time.sleep(1)
            progress.update(task, advance=1)
    
    # Check order status
    print_step(f"Checking order status: {ctx.order_number}")
    response, _ = api_call("GET", f"{ORDER_SERVICE_URL}/api/v1/orders/{ctx.order_number}")
    
    if response.status_code == 200:
        data = response.json()
        print_result("Order Status", data.get("status"), True)
        print_result("Payment Status", data.get("paymentStatus", "N/A"))
        print_result("Customer", data.get("customerInfo", {}).get("name"))
        print_result("Email", data.get("customerInfo", {}).get("email"))
        print_result("Subtotal", f"${data.get('subtotal')}")
        print_result("Items", len(data.get("items", [])))
        
        # Display order items
        table = Table(title=f"Order {ctx.order_number}", box=box.ROUNDED)
        table.add_column("Product", style="cyan")
        table.add_column("Quantity", justify="right", style="green")
        table.add_column("Price", justify="right", style="yellow")
        table.add_column("Subtotal", justify="right", style="green")
        
        for item in data.get("items", []):
            table.add_row(
                item.get("productName", ""),
                str(item.get("quantity", 0)),
                f"${item.get('priceSnapshot', 0):.2f}",
                f"${item.get('subtotal', 0):.2f}"
            )
        
        console.print(table)
        ctx.results.append({"test": "order_processing", "status": "success"})
    else:
        print_step(f"Failed to get order: {response.status_code}", "error")
        ctx.results.append({"test": "order_processing", "status": "failed"})


@app.command()
def verify_database():
    """Verify database state"""
    print_header("🗄️ Database Verification")
    
    # Check customer database
    print_step("Checking customer database...")
    try:
        conn = psycopg2.connect(**POSTGRES_CUSTOMER_CONFIG)
        cur = conn.cursor()
        
        # Count records
        cur.execute("SELECT COUNT(*) FROM categories")
        category_count = cur.fetchone()[0]
        print_result("Categories", category_count, category_count > 0)
        
        cur.execute("SELECT COUNT(*) FROM products")
        product_count = cur.fetchone()[0]
        print_result("Products", product_count, product_count > 0)
        
        cur.execute("SELECT COUNT(*) FROM carts")
        cart_count = cur.fetchone()[0]
        print_result("Carts", cart_count)
        
        cur.execute("SELECT COUNT(*) FROM cart_items")
        cart_item_count = cur.fetchone()[0]
        print_result("Cart Items", cart_item_count)
        
        cur.close()
        conn.close()
        ctx.results.append({"test": "customer_db", "status": "success"})
    except Exception as e:
        print_step(f"Database check failed: {e}", "error")
        ctx.results.append({"test": "customer_db", "status": "failed"})
    
    # Check order database
    print_step("Checking order database...")
    try:
        conn = psycopg2.connect(**POSTGRES_ORDER_CONFIG)
        cur = conn.cursor()
        
        cur.execute("SELECT COUNT(*) FROM orders")
        order_count = cur.fetchone()[0]
        print_result("Orders", order_count, order_count > 0)
        
        cur.execute("SELECT COUNT(*) FROM order_items")
        order_item_count = cur.fetchone()[0]
        print_result("Order Items", order_item_count, order_item_count > 0)
        
        cur.execute("SELECT COUNT(*) FROM payment_transactions")
        payment_count = cur.fetchone()[0]
        print_result("Payment Transactions", payment_count, payment_count > 0)
        
        cur.execute("SELECT COUNT(*) FROM processed_events")
        event_count = cur.fetchone()[0]
        print_result("Processed Events", event_count, event_count > 0)
        
        # Get latest order details
        if ctx.order_number:
            cur.execute("""
                SELECT status, subtotal, customer_email, created_at
                FROM orders
                WHERE order_number = %s
            """, (ctx.order_number,))
            order = cur.fetchone()
            if order:
                console.print()
                print_result("Order in DB", "✓", True)
                print_result("Status", order[0], indent=2)
                print_result("Subtotal", f"${order[1]}", indent=2)
                print_result("Customer", order[2], indent=2)
        
        cur.close()
        conn.close()
        ctx.results.append({"test": "order_db", "status": "success"})
    except Exception as e:
        print_step(f"Database check failed: {e}", "error")
        ctx.results.append({"test": "order_db", "status": "failed"})
    
    # Check Redis
    print_step("Checking Redis cache...")
    try:
        r = redis.Redis(**REDIS_CONFIG)
        keys = r.keys("*")
        print_result("Cache Keys", len(keys))
        for key in keys[:5]:  # Show first 5 keys
            print_result(key, r.type(key), indent=2)
        ctx.results.append({"test": "redis", "status": "success"})
    except Exception as e:
        print_step(f"Redis check failed: {e}", "error")
        ctx.results.append({"test": "redis", "status": "failed"})


@app.command()
def verify_kafka():
    """Verify Kafka topics and messages"""
    print_header("📨 Kafka Verification")
    
    print_step("Checking Kafka topics...")
    
    topics = ["orders.created", "payments.completed"]
    
    for topic in topics:
        try:
            print_step(f"Reading from topic: {topic}")
            consumer = KafkaConsumer(
                topic,
                **KAFKA_CONFIG,
                value_deserializer=lambda m: json.loads(m.decode('utf-8'))
            )
            
            messages = []
            for message in consumer:
                messages.append(message.value)
                if len(messages) >= 5:  # Limit to last 5 messages
                    break
            
            consumer.close()
            
            print_result("Messages Found", len(messages), len(messages) > 0)
            
            if messages:
                console.print()
                for i, msg in enumerate(messages[-3:], 1):  # Show last 3
                    console.print(f"  [cyan]Message {i}:[/cyan]")
                    console.print(f"    Event Type: {msg.get('eventType', 'N/A')}")
                    console.print(f"    Order Number: {msg.get('orderNumber', 'N/A')}")
                    console.print(f"    Timestamp: {msg.get('timestamp', 'N/A')}")
            
            ctx.results.append({"test": f"kafka_{topic}", "status": "success"})
        except Exception as e:
            print_step(f"Failed to read topic {topic}: {e}", "error")
            ctx.results.append({"test": f"kafka_{topic}", "status": "failed"})


def print_summary():
    """Print test execution summary"""
    if not ctx.results:
        return
        
    print_header("📊 Test Summary")
    
    success_count = sum(1 for r in ctx.results if r["status"] == "success")
    failed_count = sum(1 for r in ctx.results if r["status"] == "failed")
    total_count = len(ctx.results)
    
    # Create summary table
    table = Table(title="Test Results", box=box.DOUBLE_EDGE)
    table.add_column("Test", style="cyan")
    table.add_column("Status", justify="center")
    
    for result in ctx.results:
        status_emoji = "✅" if result["status"] == "success" else "❌"
        status_color = "green" if result["status"] == "success" else "red"
        table.add_row(
            result["test"],
            f"[{status_color}]{status_emoji} {result['status'].upper()}[/{status_color}]"
        )
    
    console.print(table)
    console.print()
    
    # Print overall status
    if failed_count == 0:
        console.print(Panel(
            f"[bold green]🎉 ALL TESTS PASSED! ({success_count}/{total_count})[/bold green]",
            box=box.DOUBLE
        ))
    else:
        console.print(Panel(
            f"[bold yellow]⚠️ SOME TESTS FAILED[/bold yellow]\n"
            f"Passed: {success_count}/{total_count}\n"
            f"Failed: {failed_count}/{total_count}",
            box=box.DOUBLE
        ))


@app.callback(invoke_without_command=True)
def main(ctx: typer.Context):
    """E-Commerce Platform Interactive Testing Tool

    When run without a command, starts in interactive mode.
    """
    if ctx.invoked_subcommand is None:
        # No command specified, run interactive mode
        interactive()


if __name__ == "__main__":
    app()
