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
from datetime import datetime, timedelta
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
    "password": "dev_password_customer"
}
POSTGRES_ORDER_CONFIG = {
    "host": "localhost",
    "port": 5433,
    "database": "order_db",
    "user": "order_user",
    "password": "dev_password_order"
}
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", "dev_redis_password")
if REDIS_PASSWORD == "":
    REDIS_PASSWORD = None

REDIS_CONFIG = {
    "host": os.getenv("REDIS_HOST", "localhost"),
    "port": int(os.getenv("REDIS_PORT", "6379")),
    "password": REDIS_PASSWORD,
    "decode_responses": True
}
KAFKA_CONFIG = {
    "bootstrap_servers": ["localhost:9092"],
    "auto_offset_reset": "latest",
    "consumer_timeout_ms": 5000
}

# Docker configuration
DOCKER_COMPOSE_FILE = "docker-compose.yml"
DOCKER_PROJECT_NAME = "ecommerce-platform"

# Global state
containers_managed = False
cleanup_on_exit = True
containers_initialized = False


class AuthState:
    """Holds authentication state"""
    def __init__(self):
        self.token: Optional[str] = None
        self.token_type: str = "Bearer"
        self.expires_at: Optional[datetime] = None
        self.username: Optional[str] = None
        self.roles: List[str] = []
        self.permissions: List[str] = []

    def is_authenticated(self) -> bool:
        """Check if user is authenticated with a valid token"""
        if not self.token or not self.expires_at:
            return False
        return datetime.now() < self.expires_at

    def is_token_expiring_soon(self, minutes: int = 5) -> bool:
        """Check if token will expire within specified minutes"""
        if not self.expires_at:
            return False
        return datetime.now() + timedelta(minutes=minutes) >= self.expires_at

    def get_time_until_expiry(self) -> Optional[timedelta]:
        """Get time until token expires"""
        if not self.expires_at:
            return None
        return self.expires_at - datetime.now()

    def clear(self):
        """Clear authentication state"""
        self.token = None
        self.expires_at = None
        self.username = None
        self.roles.clear()
        self.permissions.clear()


class TestContext:
    """Holds test execution context and results"""
    def __init__(self):
        self.auth = AuthState()
        # Legacy field for backward compatibility
        self.manager_token = None
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


def print_auth_status():
    """Display current authentication status"""
    console.print()
    console.print("[bold cyan]🔐 Authentication Status[/bold cyan]")

    if ctx.auth.is_authenticated():
        # Authenticated
        console.print("┌─ Status: [green]AUTHENTICATED[/green]")

        # User info
        console.print(f"├─ User: {ctx.auth.username}")
        console.print(f"├─ Roles: {', '.join(ctx.auth.roles) if ctx.auth.roles else 'None'}")

        # Token expiry
        time_until_expiry = ctx.auth.get_time_until_expiry()
        if time_until_expiry:
            total_seconds = int(time_until_expiry.total_seconds())
            minutes, seconds = divmod(total_seconds, 60)

            if total_seconds > 300:  # More than 5 minutes
                expiry_color = "green"
            elif total_seconds > 60:  # More than 1 minute
                expiry_color = "yellow"
            else:
                expiry_color = "red"

            console.print(f"├─ Token expires: [{expiry_color}]{minutes}m {seconds}s[/{expiry_color}]")
        else:
            console.print("├─ Token expires: [red]Unknown[/red]")

        # Permissions
        if ctx.auth.permissions:
            console.print("├─ Permissions:")
            for perm in ctx.auth.permissions:
                console.print(f"│  └─ {perm}")
        else:
            console.print("└─ Permissions: None specified")

    else:
        # Not authenticated
        console.print("┌─ Status: [red]NOT AUTHENTICATED[/red]")
        console.print("└─ Message: Use 'Login' to authenticate and access protected endpoints")


def format_auth_error_message(endpoint: str, method: str) -> str:
    """Format a helpful authentication error message"""
    return f"""
[yellow]⚠️  Authentication Required[/yellow]

The endpoint [cyan]{method} {endpoint}[/cyan] requires authentication.

Available users:
  • [green]manager/manager123[/green] - ROLE_MANAGER (can create categories/products)
  • [blue]guest/guest123[/blue] - ROLE_GUEST (read-only access)

Use the [bold]Authentication[/bold] option from the main menu to login.
"""


def get_compact_auth_status() -> str:
    """Get a compact authentication status string for menu headers"""
    if ctx.auth.is_authenticated():
        username = ctx.auth.username or "unknown"
        roles = ", ".join(ctx.auth.roles) if ctx.auth.roles else "none"

        time_until_expiry = ctx.auth.get_time_until_expiry()
        if time_until_expiry:
            total_seconds = int(time_until_expiry.total_seconds())
            if total_seconds < 60:
                expiry_info = f"⚠️ {total_seconds}s"
            elif total_seconds < 300:
                expiry_info = f"🟡 {total_seconds//60}m"
            else:
                expiry_info = f"🟢 {total_seconds//60}m"
        else:
            expiry_info = "❓"

        return f"[green]✓ {username} ({roles}) - {expiry_info}[/green]"
    else:
        return "[red]✗ Not authenticated[/red]"


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


def start_containers(detach: bool = True, force_build: bool = False):
    """Start Docker containers"""
    global containers_managed, cleanup_on_exit, containers_initialized

    print_header("🐳 Starting Docker Containers", "Launching infrastructure and services")

    infrastructure_dir = Path(__file__).parent.parent

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
        "-f", "docker-compose.yml",
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
        "-f", "docker-compose.yml",
        "-p", DOCKER_PROJECT_NAME,
        "up",
        "-d"
    ]

    if force_build:
        cmd.append("--build")

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
    containers_initialized = True

    # Wait for services to be healthy
    print_step("Waiting for services to be healthy...")
    wait_for_services()


def stop_containers():
    """Stop Docker containers"""
    print_header("🛑 Stopping Docker Containers")
    global containers_initialized, containers_managed

    infrastructure_dir = Path(__file__).parent.parent
    
    print_step("Stopping containers...")
    cmd = [
        "docker-compose",
        "-f", "docker-compose.yml",
        "-p", DOCKER_PROJECT_NAME,
        "down"
    ]
    
    code, stdout, stderr = run_command(cmd, cwd=str(infrastructure_dir))
    
    if code == 0:
        print_step("Containers stopped successfully", "success")
    else:
        print_step(f"Failed to stop containers: {stderr}", "error")

    containers_initialized = False
    containers_managed = False


def check_containers_status() -> bool:
    """Check if containers are running"""
    infrastructure_dir = Path(__file__).parent.parent
    cmd = [
        "docker-compose",
        "-f", "docker-compose.yml",
        "-p", DOCKER_PROJECT_NAME,
        "ps",
        "-q"
    ]
    code, stdout, _ = run_command(cmd, cwd=str(infrastructure_dir))
    return code == 0 and bool(stdout.strip())


def get_containers_status() -> Dict[str, str]:
    """Get detailed status of all containers"""
    infrastructure_dir = Path(__file__).parent.parent
    cmd = [
        "docker-compose",
        "-f", "docker-compose.yml",
        "-p", DOCKER_PROJECT_NAME,
        "ps"
    ]
    code, stdout, _ = run_command(cmd, cwd=str(infrastructure_dir))

    container_status = {}
    if code == 0 and stdout:
        lines = stdout.strip().split('\n')
        if len(lines) > 1:  # Skip header line
            for line in lines[1:]:
                if line.strip():
                    # Use a more robust parsing approach
                    # The format is: NAME | IMAGE | COMMAND | SERVICE | CREATED | STATUS | PORTS
                    # We'll find the STATUS by looking for "Up" or "Exit" patterns
                    parts = line.split()

                    # Find the container name (first column)
                    container_name = parts[0] if parts else ""

                    # Find the status by looking for patterns like "Up", "Exit", etc.
                    status_start = -1
                    for i, part in enumerate(parts):
                        if part in ["Up", "Exit", "Created", "Restarting"]:
                            status_start = i
                            break

                    if status_start >= 0:
                        # Join status parts until we hit PORTS (which starts with 0.0.0.0)
                        status_parts = []
                        for i in range(status_start, len(parts)):
                            if parts[i].startswith('0.0.0.0:'):
                                break
                            status_parts.append(parts[i])
                        status = ' '.join(status_parts)
                        container_status[container_name] = status

    return container_status


def check_containers_health() -> bool:
    """Check if all required containers are running and healthy"""
    container_status = get_containers_status()

    required_containers = [
        "ecommerce-services-customer-facing",
        "ecommerce-services-order-management",
        "ecommerce-infrastructure-postgres-customer",
        "ecommerce-infrastructure-postgres-order",
        "ecommerce-infrastructure-redis",
        "ecommerce-infrastructure-redpanda"
    ]

    all_running = all(
        container in container_status and "Up" in container_status[container]
        for container in required_containers
    )

    return all_running


def ensure_containers_started(force_build: bool = False):
    """Ensure required containers are running, optionally rebuilding images."""
    global containers_initialized

    if force_build:
        print_step("Force rebuild requested. Restarting containers...", "info")
        start_containers(detach=False, force_build=True)
        containers_initialized = True
        return

    if containers_initialized:
        return

    # Check if containers are already running and healthy
    if check_containers_health():
        print_step("All required containers are running and healthy", "success")
        containers_initialized = True
        return

    # Check if any containers exist for this project (might be stopped or unhealthy)
    if check_containers_status():
        print_step("Containers exist but may not be healthy. Restarting...", "warning")
        # Try to restart existing containers first
        try:
            infrastructure_dir = Path(__file__).parent.parent
            restart_cmd = [
                "docker-compose",
                "-f", "docker-compose.yml",
                "-p", DOCKER_PROJECT_NAME,
                "restart"
            ]
            code, stdout, stderr = run_command(restart_cmd, cwd=str(infrastructure_dir))
            if code == 0:
                print_step("Containers restarted successfully", "success")
                containers_initialized = True
                return
            else:
                print_step("Failed to restart containers, trying fresh start...", "warning")
        except Exception as e:
            print_step(f"Error restarting containers: {e}", "warning")

    # Try to start containers fresh
    try:
        start_containers(detach=False, force_build=False)
        containers_initialized = True
    except Exception as e:
        print_step(f"Failed to start containers: {e}", "error")
        print_step("Checking if containers might already be running under different names...", "info")

        # Check if the services are actually accessible despite the error
        if _check_services_accessible():
            print_step("Services appear to be accessible despite container startup issues", "warning")
            print_step("Proceeding with testing - some container management features may be limited", "info")
            containers_initialized = True
        else:
            raise typer.Exit(1)


def _check_services_accessible() -> bool:
    """Check if the required services are accessible despite container management issues."""
    services = [
        (CUSTOMER_SERVICE_URL, "Customer Service"),
        (ORDER_SERVICE_URL, "Order Service")
    ]

    accessible_count = 0
    for url, name in services:
        try:
            response = requests.get(f"{url}/actuator/health", timeout=5)
            if response.status_code == 200:
                data = response.json()
                if data.get("status") == "UP":
                    accessible_count += 1
                    print_step(f"{name} is accessible", "success")
                else:
                    print_step(f"{name} health check failed", "warning")
            else:
                print_step(f"{name} returned status {response.status_code}", "warning")
        except Exception as e:
            print_step(f"{name} not accessible: {e}", "warning")

    # Consider services accessible if at least the customer service is working
    # (since that's what we primarily test)
    return accessible_count >= 1


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


def authenticated_api_call(method: str, url: str, expected_status: int = None, requires_auth: bool = True, **kwargs) -> Tuple[requests.Response, bool]:
    """Make an API call with automatic authentication handling"""
    # Check if endpoint requires authentication
    if requires_auth:
        if not ctx.auth.is_authenticated():
            console.print(format_auth_error_message(url, method))
            # Return a fake response for consistency
            class FakeResponse:
                status_code = 401
                text = "Not authenticated"
                def json(self): return {"error": "Authentication required"}
            return FakeResponse(), False

        # Check if token is expiring soon
        if ctx.auth.is_token_expiring_soon():
            console.print("[yellow]⚠️  Token expiring soon, attempting refresh...[/yellow]")
            if not refresh_auth_token():
                console.print("[red]❌ Token refresh failed, authentication may fail[/red]")

        # Add authentication header
        headers = kwargs.get('headers', {})
        auth_header = f"{ctx.auth.token_type} {ctx.auth.token}"
        headers['Authorization'] = auth_header
        kwargs['headers'] = headers
        
        # Debug: Show we're adding auth header (only first few chars of token)
        console.print(f"[dim]  → Adding Authorization: {ctx.auth.token_type} {ctx.auth.token[:15]}...[/dim]")

    try:
        response = requests.request(method, url, **kwargs, timeout=10)
        if expected_status is not None:
            success = response.status_code == expected_status
        else:
            success = 200 <= response.status_code < 300

        # Handle authentication errors with helpful messages
        if response.status_code == 401 and requires_auth:
            console.print(format_auth_error_message(url, method))

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


def login_user(username: Optional[str] = None, password: Optional[str] = None) -> bool:
    """Authenticate user and store token"""
    if not username:
        username = Prompt.ask("Username", choices=["manager", "guest"], default="manager")

    if not password:
        if username == "manager":
            password = Prompt.ask("Password", default="manager123", password=True)
        else:
            password = Prompt.ask("Password", default="guest123", password=True)

    print_step(f"Authenticating as {username}...")

    try:
        response, success = api_call(
            "POST",
            f"{CUSTOMER_SERVICE_URL}/api/v1/auth/login",
            json={"username": username, "password": password}
        )

        if success and response.status_code == 200:
            data = response.json()
            token = data.get("accessToken")
            expires_in = data.get("expiresIn", 900)  # Default 15 minutes

            if token:
                # Store authentication state
                ctx.auth.token = token.strip()  # Remove any whitespace
                ctx.auth.token_type = data.get("tokenType", "Bearer").strip()
                ctx.auth.expires_at = datetime.now() + timedelta(seconds=expires_in)
                ctx.auth.username = username

                # Extract roles from JWT (simplified - in real JWT we'd decode)
                # For now, we'll map based on known users
                if username == "manager":
                    ctx.auth.roles = ["MANAGER"]
                    ctx.auth.permissions = ["CREATE_CATEGORIES", "CREATE_PRODUCTS", "UPDATE_PRODUCTS", "DELETE_PRODUCTS"]
                elif username == "guest":
                    ctx.auth.roles = ["GUEST"]
                    ctx.auth.permissions = ["READ_CATEGORIES", "READ_PRODUCTS"]

                # Backward compatibility
                ctx.manager_token = token.strip()

                print_result("Login successful", "✓", True)
                print_auth_status()
                return True
            else:
                print_step("Login failed: No token received", "error")
                return False
        else:
            print_step(f"Login failed: {response.status_code}", "error")
            if response.status_code == 401:
                console.print("[red]Invalid credentials. Try manager/manager123 or guest/guest123[/red]")
            return False

    except Exception as e:
        print_step(f"Login error: {e}", "error")
        return False


def logout_user():
    """Clear authentication state"""
    print_step("Logging out...")
    ctx.auth.clear()
    ctx.manager_token = None  # Backward compatibility
    print_result("Logout successful", "✓", True)
    print_auth_status()


def refresh_auth_token() -> bool:
    """Refresh authentication token if possible"""
    if not ctx.auth.username:
        return False

    print_step("Refreshing authentication token...")

    # For mock auth, we just login again
    return login_user(ctx.auth.username)


def check_auth_status():
    """Display current authentication status"""
    print_auth_status()


@app.command()
def start(
    interactive: bool = typer.Option(True, "--interactive/--no-interactive", "-i/-ni", help="Run in interactive mode"),
    keep_alive: bool = typer.Option(False, "--keep-alive", "-k", help="Keep containers running after tests"),
    detach: bool = typer.Option(False, "--detach", "-d", help="Start containers and exit (persistent)"),
    build: bool = typer.Option(False, "--build", "-b", help="Force rebuild images from Dockerfiles before starting containers"),
):
    """Start the platform and run interactive tests"""
    global cleanup_on_exit

    print_header(
        "🚀 E-Commerce Platform Interactive Testing",
        "Complete end-to-end testing with Docker container management"
    )

    # Check current container status
    console.print("\n🔍 Checking current container status...")
    container_status = get_containers_status()

    if container_status:
        console.print("\n📊 Current containers:")
        table = Table(box=box.ROUNDED)
        table.add_column("Container", style="cyan")
        table.add_column("Status", style="green")

        for container, status in container_status.items():
            table.add_row(container, status)

        console.print(table)

        # Check if all required containers are healthy
        all_healthy = check_containers_health()

        if all_healthy:
            console.print("\n✅ [green]All required containers appear to be running and healthy![/green]")
            console.print("   (They seem correct!)")

            console.print("\nDo you want to:")
            console.print("  [cyan]1.[/cyan] Start containers from scratch")
            console.print("  [cyan]2.[/cyan] Use existing containers [green](recommended)[/green]")
            console.print("  [cyan]3.[/cyan] Stop containers and exit")

            choice = Prompt.ask(
                "Select option",
                choices=["1", "2", "3"],
                default="2"
            )

            if choice == "1":
                console.print("\n🔄 Starting containers from scratch...")
                start_containers(detach=detach, force_build=build)
            elif choice == "2":
                console.print("\n✅ Using existing containers...")
                global containers_initialized
                containers_initialized = True
            elif choice == "3":
                console.print("\n🛑 Stopping existing containers...")
                stop_containers()
                console.print("✅ Containers stopped. Exiting.")
                return
        else:
            console.print("\n⚠️  [yellow]Some containers are not running or healthy.[/yellow]")
            console.print("   Recommended: Start containers from scratch.")

            if Confirm.ask("Start containers from scratch?", default=True):
                start_containers(detach=detach, force_build=build)
            else:
                console.print("❌ Cannot proceed without healthy containers. Exiting.")
                return
    else:
        console.print("\n📦 No containers currently running.")
        console.print("   Starting containers from scratch...")
        start_containers(detach=detach, force_build=build)
    
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
        interactive_entry()
    else:
        # Run full flow automatically
        run_full_flow()
    
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


def interactive_entry(force_build: bool = False):
    """Run interactive testing mode (requires containers to be running)"""
    print_header("🎮 Interactive Testing Mode")

    ensure_containers_started(force_build=force_build)

    interactive_mode()


@app.command()
def interactive(
    build: bool = typer.Option(False, "--build", "-b", help="Force rebuild images from Dockerfiles before starting containers"),
):
    """Run interactive testing mode (ensures containers are running)"""
    interactive_entry(force_build=build)


def show_main_menu():
    """Show the main menu with context-aware options"""
    while True:
        console.print()

        # Show status overview
        auth_status = get_compact_auth_status()
        data_status = get_data_status()
        service_status = get_service_status()

        # Create status panel
        status_lines = []
        status_lines.append(f"🔐 {auth_status}")
        status_lines.append(f"📦 {data_status}")
        status_lines.append(f"⚙️  {service_status}")

        console.print(Panel(
            f"[bold cyan]🧪 E-Commerce Testing Suite[/bold cyan]\n" +
            "\n".join(f"[dim]{line}[/dim]" for line in status_lines),
            box=box.DOUBLE
        ))
        console.print()

        # Context-aware menu options
        menu_options = get_context_aware_menu()

        table = Table(box=box.ROUNDED, show_header=False)
        table.add_column("Key", width=3)
        table.add_column("Action")
        table.add_column("Description")

        for key, action, desc, style in menu_options:
            # Apply styling based on the style value
            if style == "dim":
                key_style = "dim cyan"
                action_style = "dim"
                desc_style = "dim"
            elif style == "bold green":
                key_style = "bold cyan"
                action_style = "bold green"
                desc_style = "green"
            elif style == "normal":
                # Active/ready options - all undimmed
                key_style = "cyan"
                action_style = "green"
                desc_style = "white"  # Changed from "dim" to "white" for active options
            else:
                # Default to normal styling
                key_style = "cyan"
                action_style = "green"
                desc_style = "white"

            table.add_row(f"[{key_style}]{key}[/{key_style}]",
                         f"[{action_style}]{action}[/{action_style}]",
                         f"[{desc_style}]{desc}[/{desc_style}]")

        console.print(table)
        console.print()
        console.print("[dim]💡 Tip: Press the key shown or type the full option[/dim]")
        console.print()

        # Smart default based on context
        default_choice = get_smart_default(menu_options)
        choice = Prompt.ask("Choose action", default=default_choice)

        # Handle choice
        result = handle_menu_choice(choice, menu_options)
        if result == "quit":
            break
        elif result == "continue":
            continue

        console.print()
        if not Confirm.ask("Continue?", default=True):
            break

    print_summary()


def get_context_aware_menu():
    """Return menu options based on current context with styling"""
    base_options = []

    # Always available - health check
    base_options.append(("h", "Health Check", "Verify all services are running", "normal"))

    # Auth-related options - highlight when services are ready but not authenticated
    is_authenticated = ctx.auth.is_authenticated()
    services_ready = containers_initialized

    if not is_authenticated:
        # Dimmed if services not ready, normal if services ready
        auth_style = "normal" if services_ready else "dim"
        base_options.append(("a", "🔐 Login", "Authenticate to unlock full features", auth_style))
    else:
        base_options.append(("a", "🔐 Account", "View/change authentication", "normal"))

    # Data creation/management - highlight when authenticated but no data
    has_data = has_test_data()
    if not has_data:
        # Dimmed if not authenticated, highlighted if authenticated and no data
        data_style = "bold green" if is_authenticated else "dim"
        base_options.append(("d", "📦 Create Data", "Set up sample categories and products", data_style))
    else:
        base_options.append(("d", "📦 Manage Data", "View/edit test data", "normal"))

    # Testing options - only available when authenticated and have data
    if is_authenticated:
        if has_data:
            # All testing options available - normal styling
            base_options.append(("t", "🧪 Quick Test", "Run common test scenarios", "normal"))
            base_options.append(("f", "🚀 Full Flow", "Complete end-to-end journey", "normal"))
            base_options.append(("i", "🔧 Individual Tests", "Test specific endpoints", "normal"))
        else:
            # Basic tests available but dimmed since no data
            base_options.append(("t", "🧪 Basic Tests", "Run tests with existing data", "dim"))

    # Advanced options - normal when authenticated, dimmed when not
    verify_style = "normal" if is_authenticated else "dim"
    error_style = "normal" if is_authenticated else "dim"
    base_options.append(("v", "📊 Verify", "Check databases and events", verify_style))
    base_options.append(("e", "❌ Error Tests", "Test error conditions", error_style))

    # Always last
    base_options.append(("q", "👋 Quit", "Exit testing suite", "normal"))

    return base_options


def get_smart_default(menu_options):
    """Return the smartest default choice based on context"""
    # If not authenticated, default to login
    if not ctx.auth.is_authenticated():
        for key, action, _, _ in menu_options:
            if "Login" in action:
                return key

    # If no data, default to create data
    if not has_test_data():
        for key, action, _, _ in menu_options:
            if "Create Data" in action:
                return key

    # If everything ready, default to quick test
    for key, action, _, _ in menu_options:
        if "Quick Test" in action or "Full Flow" in action:
            return key

    # Fallback to health check
    return "h"


def get_data_status():
    """Get current data status - checks both session and database"""
    # Start with session data
    session_categories = len(ctx.test_data.get("categories", []))
    session_products = len(ctx.product_ids)
    session_orders = 1 if ctx.order_number else 0
    
    # Try to get actual counts from database
    try:
        conn = psycopg2.connect(**POSTGRES_CUSTOMER_CONFIG)
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM categories")
        db_categories = cur.fetchone()[0]
        cur.execute("SELECT COUNT(*) FROM products")
        db_products = cur.fetchone()[0]
        cur.close()
        conn.close()
        
        # Use database counts (more accurate)
        categories = db_categories
        products = db_products
    except:
        # Fallback to session data if database check fails
        categories = session_categories
        products = session_products
    
    # Orders from session only (order service might not be accessible)
    orders = session_orders

    if categories > 0 or products > 0 or orders > 0:
        return f"Data ready: {categories} categories, {products} products, {orders} orders"
    else:
        return "No test data - create some first"


def get_service_status():
    """Get current service status"""
    if containers_initialized:
        return "Services ready"
    else:
        return "Services initializing..."


def has_test_data():
    """Check if we have basic test data - checks both session and database"""
    # Check session data first (fast)
    if len(ctx.product_ids) > 0 or len(ctx.test_data.get("categories", [])) > 0:
        return True
    
    # Check database for existing data
    try:
        conn = psycopg2.connect(**POSTGRES_CUSTOMER_CONFIG)
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM products")
        product_count = cur.fetchone()[0]
        cur.close()
        conn.close()
        return product_count > 0
    except:
        return False


def handle_menu_choice(choice, menu_options):
    """Handle menu choice and return action result"""
    choice = choice.lower().strip()

    # Find the action for this choice
    for key, action, desc, style in menu_options:
        if choice == key:
            # Map actions to functions
            action_map = {
                "h": lambda: test_health_impl(),
                "a": lambda: show_authentication_menu(),
                "d": lambda: show_data_management_menu(),
                "t": lambda: show_quick_test_menu(),
                "f": lambda: happy_path_flow(),
                "i": lambda: test_individual_endpoints(),
                "v": lambda: show_verification_menu(),
                "e": lambda: show_error_test_menu(),
                "q": lambda: "quit"
            }

            if key in action_map:
                result = action_map[key]()
                if result == "quit":
                    return "quit"
            break
    else:
        console.print(f"[red]Unknown option: {choice}[/red]")

    return "continue"


def show_quick_test_menu():
    """Show quick test options menu"""
    console.print()
    console.print(Panel("[bold cyan]🧪 Quick Tests[/bold cyan]", box=box.DOUBLE))
    console.print()

    tests = [
        ("1", "Create & View Products", "Test product catalog operations"),
        ("2", "Shopping Cart Flow", "Test cart operations"),
        ("3", "Complete Checkout", "Test full purchase flow"),
        ("b", "Back to Main Menu", "")
    ]

    table = Table(box=box.ROUNDED, show_header=False)
    table.add_column("Option", style="cyan", width=5)
    table.add_column("Test", style="green")
    table.add_column("Description", style="dim")

    for opt, test, desc in tests:
        table.add_row(opt, test, desc)

    console.print(table)
    console.print()

    choice = Prompt.ask("Select test", choices=[t[0] for t in tests], default="1")

    if choice == "1":
        if not has_test_data():
            console.print("[yellow]Creating sample data first...[/yellow]")
            create_sample_data()
        test_catalog_management_impl()
    elif choice == "2":
        test_cart_operations_impl()
    elif choice == "3":
        if not ctx.session_id:
            console.print("[yellow]Setting up cart first...[/yellow]")
            test_cart_operations_impl()
        test_checkout_impl()
    elif choice == "b":
        return

    return "continue"


def show_verification_menu():
    """Show verification options"""
    console.print()
    console.print(Panel("[bold cyan]📊 System Verification[/bold cyan]", box=box.DOUBLE))
    console.print()

    options = [
        ("1", "Database State", "Check data persistence"),
        ("2", "Event Messages", "Verify Kafka events"),
        ("3", "All Systems", "Full system check"),
        ("b", "Back to Main Menu", "")
    ]

    table = Table(box=box.ROUNDED, show_header=False)
    table.add_column("Option", style="cyan", width=5)
    table.add_column("Check", style="green")
    table.add_column("Description", style="dim")

    for opt, check, desc in options:
        table.add_row(opt, check, desc)

    console.print(table)
    console.print()

    choice = Prompt.ask("Select verification", choices=[o[0] for o in options], default="3")

    if choice == "1":
        verify_database_impl()
    elif choice == "2":
        verify_kafka_impl()
    elif choice == "3":
        verify_database_impl()
        verify_kafka_impl()
    elif choice == "b":
        return

    return "continue"


def show_error_test_menu():
    """Show error testing options"""
    console.print()
    console.print(Panel("[bold cyan]❌ Error Testing[/bold cyan]", box=box.DOUBLE))
    console.print()

    options = [
        ("1", "Validation Errors", "Test 400 Bad Request scenarios"),
        ("2", "Not Found Errors", "Test 404 resource scenarios"),
        ("3", "Cart/Checkout Errors", "Test shopping flow errors"),
        ("4", "All Error Tests", "Run complete error suite"),
        ("b", "Back to Main Menu", "")
    ]

    table = Table(box=box.ROUNDED, show_header=False)
    table.add_column("Option", style="cyan", width=5)
    table.add_column("Test Type", style="green")
    table.add_column("Description", style="dim")

    for opt, test_type, desc in options:
        table.add_row(opt, test_type, desc)

    console.print(table)
    console.print()

    choice = Prompt.ask("Select error tests", choices=[o[0] for o in options], default="4")

    if choice == "1":
        error_scenarios_400()
    elif choice == "2":
        error_scenarios_404()
    elif choice == "3":
        error_scenarios_cart_checkout()
    elif choice == "4":
        error_scenarios_400()
        error_scenarios_404()
        error_scenarios_cart_checkout()
    elif choice == "b":
        return

    return "continue"


def show_data_management_menu():
    """Show data management options"""
    console.print()
    console.print(Panel("[bold cyan]📦 Data Management[/bold cyan]", box=box.DOUBLE))
    console.print()

    options = [
        ("1", "View Current Data", "Show existing categories/products"),
        ("2", "Create Categories", "Add new product categories"),
        ("3", "Create Products", "Add new products"),
        ("4", "Create Sample Data", "Set up complete test dataset"),
        ("b", "Back to Main Menu", "")
    ]

    table = Table(box=box.ROUNDED, show_header=False)
    table.add_column("Option", style="cyan", width=5)
    table.add_column("Action", style="green")
    table.add_column("Description", style="dim")

    for opt, action, desc in options:
        table.add_row(opt, action, desc)

    console.print(table)
    console.print()

    choice = Prompt.ask("Select action", choices=[o[0] for o in options], default="4")

    if choice == "1":
        show_current_data()
    elif choice == "2":
        test_create_category_interactive()
    elif choice == "3":
        test_create_product_interactive()
    elif choice == "4":
        create_sample_data()
    elif choice == "b":
        return

    return "continue"


def show_current_data():
    """Show current test data"""
    console.print()
    console.print("[bold cyan]📊 Current Test Data[/bold cyan]")
    console.print()

    # Categories
    if ctx.test_data.get("categories"):
        console.print(f"[green]Categories ({len(ctx.test_data['categories'])}):[/green]")
        for cat in ctx.test_data["categories"]:
            console.print(f"  • {cat.get('name', 'Unknown')} ({cat.get('id', 'no-id')[:8]}...)")
    else:
        console.print("[yellow]No categories created yet[/yellow]")

    console.print()

    # Products
    if ctx.product_ids:
        console.print(f"[green]Products ({len(ctx.product_ids)}):[/green]")
        console.print(f"  • {len(ctx.product_ids)} products created")
        if ctx.category_id:
            console.print(f"  • Using category: {ctx.category_id[:8]}...")
    else:
        console.print("[yellow]No products created yet[/yellow]")

    console.print()

    # Orders
    if ctx.order_number:
        console.print(f"[green]Orders:[/green]")
        console.print(f"  • Latest order: {ctx.order_number}")
    else:
        console.print("[yellow]No orders created yet[/yellow]")


def create_sample_data():
    """Create a complete set of sample data"""
    console.print("[cyan]Creating sample test data...[/cyan]")
    
    # Ensure authentication before creating data
    if not ctx.auth.is_authenticated():
        console.print("[yellow]⚠️  Authentication required to create data. Logging in as manager...[/yellow]")
        if not login_user("manager", "manager123"):
            console.print("[red]❌ Failed to authenticate. Cannot create data.[/red]")
            return False
    
    # Verify we actually have a valid token
    if not ctx.auth.token or not ctx.auth.token.strip():
        console.print("[red]❌ Authentication token is missing or empty. Please login first.[/red]")
        return False
    
    console.print(f"[dim]Using authentication: {ctx.auth.token_type} token (first 10 chars: {ctx.auth.token[:10]}...)[/dim]")

    success_count = 0
    total_operations = 0

    # Create categories first
    if not ctx.category_id:
        total_operations += 1
        console.print("  📁 Creating categories...")
        response, success = authenticated_api_call(
            "POST",
            f"{CUSTOMER_SERVICE_URL}/api/v1/categories",
            json={"name": "Electronics", "description": "Electronic devices and gadgets"}
        )
        if success and response.status_code == 201:
            data = response.json()
            ctx.category_id = data.get("id")
            ctx.test_data["categories"].append({"id": ctx.category_id, "name": "Electronics"})
            console.print(f"    [green]✓ Category created: {ctx.category_id}[/green]")
            success_count += 1
        else:
            console.print(f"    [red]✗ Failed to create category (status: {response.status_code})[/red]")
            console.print(f"      Response: {response.text[:200]}...")
            return False

    # Only create products if we have a category
    if ctx.category_id and len(ctx.product_ids) < 2:
        console.print("  📦 Creating sample products...")

        products = [
            {
                "name": "Wireless Headphones",
                "sku": f"HEADPHONES-{uuid.uuid4().hex[:6].upper()}",
                "description": "High-quality wireless headphones",
                "price": 99.99,
                "inventoryQuantity": 50
            },
            {
                "name": "Phone Case",
                "sku": f"CASE-{uuid.uuid4().hex[:6].upper()}",
                "description": "Protective phone case",
                "price": 24.99,
                "inventoryQuantity": 100
            }
        ]

        for i, product in enumerate(products, 1):
            total_operations += 1
            console.print(f"    Creating product {i}: {product['name']}")
            response, success = authenticated_api_call(
                "POST",
                f"{CUSTOMER_SERVICE_URL}/api/v1/products",
                json={
                    **product,
                    "categoryId": ctx.category_id
                }
            )
            if success and response.status_code == 201:
                data = response.json()
                product_id = data.get("id")
                ctx.product_ids.append(product_id)
                console.print(f"      [green]✓ Product created: {product_id}[/green]")
                success_count += 1
            else:
                console.print(f"      [red]✗ Failed to create product (status: {response.status_code})[/red]")
                console.print(f"        Response: {response.text[:200]}...")

    if success_count == total_operations:
        console.print("[green]✅ Sample data created successfully![/green]")
        return True
    else:
        console.print(f"[yellow]⚠️  Partial success: {success_count}/{total_operations} operations succeeded[/yellow]")
        return False


def interactive_mode():
    """Enhanced interactive testing mode with improved UX"""
    ensure_containers_started()
    show_main_menu()


def show_authentication_menu():
    """Show authentication management menu with improved UX"""
    console.print()
    console.print(Panel("[bold cyan]🔐 Authentication[/bold cyan]", box=box.DOUBLE))
    console.print()

    # Show current auth status prominently
    print_auth_status()
    console.print()

    if not ctx.auth.is_authenticated():
        # Login options when not authenticated
        console.print("[bold cyan]Choose login method:[/bold cyan]")
        options = [
            ("1", "Manager Login", "manager/manager123 (full access)"),
            ("2", "Guest Login", "guest/guest123 (read-only)"),
            ("3", "Custom Login", "Enter your own credentials"),
            ("b", "Back to Main Menu", "")
        ]
    else:
        # Account management when authenticated
        console.print("[bold cyan]Account options:[/bold cyan]")
        options = [
            ("1", "Switch to Manager", "Login as manager"),
            ("2", "Switch to Guest", "Login as guest"),
            ("3", "Custom Login", "Enter different credentials"),
            ("4", "Logout", "Clear current session"),
            ("5", "Refresh Token", "Extend session if expiring"),
            ("b", "Back to Main Menu", "")
        ]

    table = Table(box=box.ROUNDED, show_header=False)
    table.add_column("Option", style="cyan", width=5)
    table.add_column("Action", style="green")
    table.add_column("Description", style="dim")

    for opt, action, desc in options:
        table.add_row(opt, action, desc)

    console.print(table)
    console.print()

    if not ctx.auth.is_authenticated():
        default_choice = "1"  # Default to manager login
        valid_choices = ["1", "2", "3", "b"]
    else:
        default_choice = "5"  # Default to refresh token
        valid_choices = ["1", "2", "3", "4", "5", "b"]

    choice = Prompt.ask("Choose action", choices=valid_choices, default=default_choice)

    if choice == "b":
        return "continue"
    elif choice == "1":
        login_user("manager", "manager123")
    elif choice == "2":
        login_user("guest", "guest123")
    elif choice == "3":
        login_user()
    elif choice == "4" and ctx.auth.is_authenticated():
        logout_user()
    elif choice == "5" and ctx.auth.is_authenticated():
        refresh_auth_token()

    return "continue"


def happy_path_flow():
    """Run complete happy path flow"""
    print_header("✨ Happy Path - Complete Flow")
    
    ctx.results.clear()
    ctx.session_id = f"test-session-{uuid.uuid4()}"
    
    test_health_impl()
    test_auth_impl()
    test_catalog_management_impl()
    test_cart_operations_impl()
    test_checkout_impl()
    test_order_processing_impl()


def test_individual_endpoints():
    """Test individual endpoints interactively with improved UX"""
    console.print()
    console.print(Panel("[bold cyan]🔧 Individual Endpoint Testing[/bold cyan]", box=box.DOUBLE))

    # Show auth status
    auth_status = get_compact_auth_status()
    console.print(f"[dim]Auth Status: {auth_status}[/dim]")
    console.print()

    # Group endpoints by service and access level
    endpoint_groups = [
        ("[cyan]📂 Catalog Endpoints[/cyan]", [
            ("1", "GET /categories", "List categories", "public", test_get_categories),
            ("2", "POST /categories", "Create category", "manager", test_create_category_interactive),
            ("3", "GET /products", "List products", "public", test_get_products),
            ("4", "POST /products", "Create product", "manager", test_create_product_interactive),
        ]),
        ("[cyan]🛒 Cart Endpoints[/cyan]", [
            ("5", "GET /carts/{id}", "View cart", "public", test_get_cart_interactive),
            ("6", "POST /carts/{id}/items", "Add to cart", "public", test_add_to_cart_interactive),
        ]),
        ("[cyan]💳 Checkout & Orders[/cyan]", [
            ("7", "POST /checkout", "Checkout cart", "public", test_checkout_interactive),
            ("8", "GET /orders/{id}", "View order", "public", test_get_order_interactive),
        ])
    ]

    for group_name, endpoints in endpoint_groups:
        console.print(group_name)
        table = Table(box=box.ROUNDED, show_header=False)
        table.add_column("Key", style="cyan", width=3)
        table.add_column("Method", style="green", width=20)
        table.add_column("Description", style="dim")
        table.add_column("Access", style="yellow", width=8)

        for key, endpoint, desc, access, _ in endpoints:
            access_icon = "🔓" if access == "public" else "🔐"
            table.add_row(key, endpoint, desc, f"{access_icon} {access}")

        console.print(table)
        console.print()

    console.print("[cyan]Navigation:[/cyan]")
    console.print("  [green]b[/green] - Back to main menu")
    console.print()
    console.print("[dim]💡 Endpoints marked with 🔐 require authentication[/dim]")
    console.print()

    # Get all valid choices
    all_choices = ["b"]
    for _, endpoints in endpoint_groups:
        for key, _, _, _, _ in endpoints:
            all_choices.append(key)

    choice = Prompt.ask("Select endpoint to test", choices=all_choices, default="1")

    if choice == "b":
        return "continue"

    # Find and execute the selected endpoint test
    for _, endpoints in endpoint_groups:
        for key, _, _, _, func in endpoints:
            if key == choice:
                try:
                    func()
                    return "continue"
                except Exception as e:
                    console.print(f"[red]❌ Error testing endpoint: {e}[/red]")
                    return "continue"

    return "continue"


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
    """Test 404 Not Found scenarios with service availability checks"""
    print_header("🔍 Error Scenarios - 404 Not Found", "Testing non-existent resources")

    console.print("[bold]Testing resource not found scenarios...[/bold]\n")

    # Test 1: Get non-existent category (Customer Service)
    console.print("[cyan]🛍️  Testing Customer Service endpoints...[/cyan]")
    fake_id = str(uuid.uuid4())
    print_step(f"Test: Get non-existent category (ID: {fake_id})")
    try:
        response, _ = api_call(
            "GET",
            f"{CUSTOMER_SERVICE_URL}/api/v1/categories/{fake_id}",
            expected_status=404
        )
        show_response(response, success=(response.status_code == 404))
        ctx.results.append({"test": "404_category", "status": "success" if response.status_code == 404 else "failed"})
    except Exception as e:
        print_step(f"❌ Customer Service unavailable: {e}", "error")
        ctx.results.append({"test": "404_category", "status": "skipped"})

    # Test 2: Get non-existent product (Customer Service)
    fake_id = str(uuid.uuid4())
    print_step(f"Test: Get non-existent product (ID: {fake_id})")
    try:
        response, _ = api_call(
            "GET",
            f"{CUSTOMER_SERVICE_URL}/api/v1/products/{fake_id}",
            expected_status=404
        )
        show_response(response, success=(response.status_code == 404))
        ctx.results.append({"test": "404_product", "status": "success" if response.status_code == 404 else "failed"})
    except Exception as e:
        print_step(f"❌ Customer Service unavailable: {e}", "error")
        ctx.results.append({"test": "404_product", "status": "skipped"})

    # Test 3: Get non-existent order (Order Service - may not be available)
    console.print("\n[cyan]📦 Testing Order Service endpoints...[/cyan]")
    print_step("Test: Get non-existent order")
    try:
        response, _ = api_call(
            "GET",
            f"{ORDER_SERVICE_URL}/api/v1/orders/ORD-99999999-999",
            expected_status=404
        )
        show_response(response, success=(response.status_code == 404))
        ctx.results.append({"test": "404_order", "status": "success" if response.status_code == 404 else "failed"})
    except Exception as e:
        print_step(f"⚠️  Order Service unavailable (skipping order tests): {str(e)[:50]}...", "warning")
        ctx.results.append({"test": "404_order", "status": "skipped"})
        console.print("[dim]💡 Order Service tests skipped - service may not be running[/dim]")

    # Test 4: Update non-existent category (Customer Service)
    console.print("\n[cyan]🔄 Testing update operations...[/cyan]")
    fake_id = str(uuid.uuid4())
    print_step(f"Test: Update non-existent category (ID: {fake_id})")
    try:
        response, _ = api_call(
            "PUT",
            f"{CUSTOMER_SERVICE_URL}/api/v1/categories/{fake_id}",
            expected_status=404,
            json={"name": "Updated Name", "description": "Updated Description"}
        )
        show_response(response, success=(response.status_code == 404))
        ctx.results.append({"test": "404_update_category", "status": "success" if response.status_code == 404 else "failed"})
    except Exception as e:
        print_step(f"❌ Customer Service unavailable: {e}", "error")
        ctx.results.append({"test": "404_update_category", "status": "skipped"})

    console.print("\n[green]✅ Error testing complete![/green]")
    console.print("[dim]Note: Some tests may be skipped if services are unavailable[/dim]")


def error_scenarios_cart_checkout():
    """Test cart and checkout error scenarios with service availability checks"""
    print_header("🛒 Error Scenarios - Cart & Checkout", "Testing cart and checkout validation")

    console.print("[bold]Testing cart and checkout error scenarios...[/bold]\n")

    all_tests_skipped = True

    # Test 1: Checkout with empty cart
    console.print("[cyan]🛒 Testing cart operations...[/cyan]")
    empty_session = f"empty-{uuid.uuid4()}"
    print_step(f"Test: Checkout with empty cart (session: {empty_session})")
    try:
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
        all_tests_skipped = False
    except Exception as e:
        print_step(f"❌ Customer Service unavailable: {e}", "error")
        ctx.results.append({"test": "empty_cart_checkout", "status": "skipped"})

    # Test 2: Add non-existent product to cart
    fake_product_id = str(uuid.uuid4())
    print_step(f"Test: Add non-existent product to cart (ID: {fake_product_id})")
    try:
        response, _ = api_call(
            "POST",
            f"{CUSTOMER_SERVICE_URL}/api/v1/carts/test-session/items",
            expected_status=404,
            json={"productId": fake_product_id, "quantity": 1}
        )
        show_response(response, success=(response.status_code == 404))
        ctx.results.append({"test": "add_nonexistent_product", "status": "success" if response.status_code == 404 else "failed"})
        all_tests_skipped = False
    except Exception as e:
        print_step(f"❌ Customer Service unavailable: {e}", "error")
        ctx.results.append({"test": "add_nonexistent_product", "status": "skipped"})

    # Test 3: Checkout with incomplete address
    console.print("\n[cyan]💳 Testing checkout validation...[/cyan]")
    print_step("Test: Checkout with incomplete address")
    try:
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
                        # Missing required fields like street, state, postalCode, country
                    }
                }
            }
        )
        show_response(response, success=(response.status_code == 400))
        ctx.results.append({"test": "incomplete_address", "status": "success" if response.status_code == 400 else "failed"})
        all_tests_skipped = False
    except Exception as e:
        print_step(f"❌ Customer Service unavailable: {e}", "error")
        ctx.results.append({"test": "incomplete_address", "status": "skipped"})

    if all_tests_skipped:
        console.print("\n[yellow]⚠️  All cart/checkout tests were skipped due to service unavailability[/yellow]")
    else:
        console.print("\n[green]✅ Cart/checkout error testing complete![/green]")
        console.print("[dim]Note: Some tests may be skipped if services are unavailable[/dim]")


# Helper functions for interactive endpoint testing
def test_get_categories():
    print_step("GET /api/v1/categories")
    response, success = api_call("GET", f"{CUSTOMER_SERVICE_URL}/api/v1/categories")
    show_response(response, success)


def test_create_category_interactive():
    # Check authentication first
    if not ctx.auth.is_authenticated():
        console.print("[yellow]⚠️  Authentication required. Please login first.[/yellow]")
        return
    
    print_step("POST /api/v1/categories")
    name = Prompt.ask("Category name", default="Test Category")
    description = Prompt.ask("Category description", default="A test category")

    response, success = authenticated_api_call(
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


def get_or_select_category():
    """Get a category ID by selecting from existing categories or creating a new one."""
    # First, try to fetch existing categories (this is a public endpoint)
    response, success = authenticated_api_call("GET", f"{CUSTOMER_SERVICE_URL}/api/v1/categories", requires_auth=False)

    existing_categories = []
    if success:
        data = response.json()
        # Handle both paginated and direct array responses
        if isinstance(data, list):
            existing_categories = data
        elif isinstance(data, dict) and "content" in data:
            existing_categories = data["content"]
        elif isinstance(data, dict) and "data" in data:
            existing_categories = data["data"]

    if existing_categories:
        console.print(f"[cyan]Found {len(existing_categories)} existing categories:[/cyan]")
        table = Table(box=box.ROUNDED)
        table.add_column("#", style="cyan", width=3)
        table.add_column("ID", style="magenta", width=36)
        table.add_column("Name", style="green")
        table.add_column("Description", style="dim")

        for i, category in enumerate(existing_categories, 1):
            table.add_row(
                str(i),
                category.get("id", "")[:36],
                category.get("name", ""),
                category.get("description", "")[:50]
            )

        console.print(table)
        console.print()

        console.print("[cyan]Options:[/cyan]")
        console.print(f"  [green]1-{len(existing_categories)}[/green] - Use existing category")
        console.print("  [green]n[/green] - Create new category")
        console.print("  [green]b[/green] - Back to menu")
        console.print()

        while True:
            choice = Prompt.ask("Select category option", default="n")

            if choice.lower() == "b":
                return None
            elif choice.lower() == "n":
                # Create new category
                print_step("Creating new category...")
                test_create_category_interactive()
                return ctx.category_id
            else:
                try:
                    index = int(choice) - 1
                    if 0 <= index < len(existing_categories):
                        selected_category = existing_categories[index]
                        ctx.category_id = selected_category["id"]
                        console.print(f"[green]✓ Selected category: {selected_category['name']}[/green]")
                        return ctx.category_id
                    else:
                        console.print("[red]Invalid selection. Please try again.[/red]")
                except ValueError:
                    console.print("[red]Invalid input. Please enter a number or 'n' for new.[/red]")
    else:
        console.print("[yellow]No existing categories found. Creating a new one...[/yellow]")
        test_create_category_interactive()
        return ctx.category_id


def test_create_product_interactive():
    # Check authentication first
    if not ctx.auth.is_authenticated():
        console.print("[yellow]⚠️  Authentication required. Please login first.[/yellow]")
        return
    
    # Get or select category
    category_id = get_or_select_category()

    if category_id is None:
        console.print("[yellow]Category selection cancelled.[/yellow]")
        return

    print_step("POST /api/v1/products")
    name = Prompt.ask("Product name", default="Test Product")
    sku = Prompt.ask("SKU", default=f"SKU-{uuid.uuid4().hex[:6].upper()}")
    price = float(Prompt.ask("Price", default="29.99"))
    quantity = int(Prompt.ask("Inventory quantity", default="100"))

    response, success = authenticated_api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/products",
        json={
            "sku": sku,
            "name": name,
            "description": "A test product",
            "price": price,
            "inventoryQuantity": quantity,
            "categoryId": category_id
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
    # Add Idempotency-Key header as required by API
    response, success = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/checkout",
        headers={"Idempotency-Key": f"{uuid.uuid4()}"},
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


def run_full_flow():
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
    test_health_impl()
    test_auth_impl()
    test_catalog_management_impl()
    test_cart_operations_impl()
    test_checkout_impl()
    test_order_processing_impl()
    verify_database_impl()
    verify_kafka_impl()

    # Print summary
    print_summary()


@app.command()
def full_flow(
    build: bool = typer.Option(False, "--build", "-b", help="Force rebuild images from Dockerfiles before starting containers"),
):
    """Run complete end-to-end test flow (ensures containers are running)"""
    ensure_containers_started(force_build=build)
    run_full_flow()


def test_health_impl():
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
def test_health(
    build: bool = typer.Option(False, "--build", "-b", help="Force rebuild images from Dockerfiles before starting containers"),
):
    """Test health endpoints (ensures containers are running)"""
    ensure_containers_started(force_build=build)
    test_health_impl()


def test_auth_impl():
    """Test authentication and obtain manager token"""
    print_header("🔐 Authentication")

    success = login_user("manager", "manager123")

    if success:
        ctx.results.append({"test": "auth", "status": "success"})
    else:
        ctx.results.append({"test": "auth", "status": "failed"})


@app.command()
def test_auth(
    build: bool = typer.Option(False, "--build", "-b", help="Force rebuild images from Dockerfiles before starting containers"),
):
    """Test authentication (ensures containers are running)"""
    ensure_containers_started(force_build=build)
    test_auth_impl()


def test_catalog_management_impl():
    """Test catalog management (categories and products)"""
    print_header("📚 Catalog Management")

    if not ctx.auth.is_authenticated():
        print_step("No authentication available. Running auth first...", "warning")
        test_auth_impl()

    # Create category
    print_step("Creating category 'Electronics'...")
    response, _ = authenticated_api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/categories",
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
        response, _ = authenticated_api_call(
            "POST",
            f"{CUSTOMER_SERVICE_URL}/api/v1/products",
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
def test_catalog_management(
    build: bool = typer.Option(False, "--build", "-b", help="Force rebuild images from Dockerfiles before starting containers"),
):
    """Test catalog management (ensures containers are running)"""
    ensure_containers_started(force_build=build)
    test_catalog_management_impl()


def test_cart_operations_impl():
    """Test shopping cart operations"""
    print_header("🛒 Shopping Cart")
    
    if not ctx.product_ids:
        print_step("No products available. Running catalog creation first...", "warning")
        test_catalog_management_impl()
    
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
def test_cart_operations(
    build: bool = typer.Option(False, "--build", "-b", help="Force rebuild images from Dockerfiles before starting containers"),
):
    """Test shopping cart operations (ensures containers are running)"""
    ensure_containers_started(force_build=build)
    test_cart_operations_impl()


def test_checkout_impl():
    """Test checkout process"""
    print_header("💳 Checkout")
    
    if not ctx.session_id:
        print_step("No active cart. Creating cart first...", "warning")
        test_cart_operations_impl()
    
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
    
    # Add Idempotency-Key header as required by API
    response, _ = api_call(
        "POST",
        f"{CUSTOMER_SERVICE_URL}/api/v1/checkout",
        headers={"Idempotency-Key": f"{uuid.uuid4()}"},
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
def test_checkout(
    build: bool = typer.Option(False, "--build", "-b", help="Force rebuild images from Dockerfiles before starting containers"),
):
    """Test checkout process (ensures containers are running)"""
    ensure_containers_started(force_build=build)
    test_checkout_impl()


def test_order_processing_impl():
    """Test order processing and payment"""
    print_header("📦 Order Processing")
    
    if not ctx.order_number:
        print_step("No order available. Running checkout first...", "warning")
        test_checkout_impl()
    
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
def test_order_processing(
    build: bool = typer.Option(False, "--build", "-b", help="Force rebuild images from Dockerfiles before starting containers"),
):
    """Test order processing (ensures containers are running)"""
    ensure_containers_started(force_build=build)
    test_order_processing_impl()


def verify_database_impl():
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
def verify_database(
    build: bool = typer.Option(False, "--build", "-b", help="Force rebuild images from Dockerfiles before starting containers"),
):
    """Verify database state (ensures containers are running)"""
    ensure_containers_started(force_build=build)
    verify_database_impl()


def verify_kafka_impl():
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


@app.command()
def verify_kafka(
    build: bool = typer.Option(False, "--build", "-b", help="Force rebuild images from Dockerfiles before starting containers"),
):
    """Verify Kafka topics (ensures containers are running)"""
    ensure_containers_started(force_build=build)
    verify_kafka_impl()


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
def main(
    ctx: typer.Context,
    build: bool = typer.Option(False, "--build", "-b", help="Force rebuild images from Dockerfiles before starting containers"),
):
    """E-Commerce Platform Interactive Testing Tool

    When run without a command, starts in interactive mode.
    """
    if ctx.invoked_subcommand is None:
        # No command specified, run interactive mode
        interactive_entry(force_build=build)


if __name__ == "__main__":
    app()
