"""
CLI commands for doorbell management.
"""
import asyncio
import json
import logging
from typing import Optional
from rich.console import Console
from rich.table import Table

console = Console()
logger = logging.getLogger(__name__)


class CLI:
    def __init__(self, db, ws_client=None):
        self.db = db
        self.ws_client = ws_client

    async def cmd_devices_list(self):
        devices = await self.db.get_all_devices()
        
        table = Table(title="Registered Devices")
        table.add_column("ID", style="cyan")
        table.add_column("Name", style="green")
        table.add_column("Type", style="yellow")
        table.add_column("Status", style="magenta")
        table.add_column("Last Seen", style="white")
        
        for device in devices:
            status = "[green]Online[/green]" if device.is_online else "[red]Offline[/red]"
            last_seen = device.last_seen.strftime("%Y-%m-%d %H:%M:%S") if device.last_seen else "Never"
            table.add_row(device.id, device.name, device.device_type, status, last_seen)
        
        console.print(table)

    async def cmd_events_list(self, device_id: Optional[str] = None, limit: int = 20):
        events = await self.db.get_events(device_id=device_id, limit=limit)
        
        table = Table(title="Recent Events")
        table.add_column("Time", style="cyan")
        table.add_column("Type", style="yellow")
        table.add_column("Device", style="green")
        table.add_column("Confidence", style="magenta")
        table.add_column("Read", style="white")
        
        for event in events:
            read_status = "[green]Yes[/green]" if event.is_read else "[red]No[/red]"
            confidence = f"{event.confidence:.0%}" if event.confidence else "-"
            timestamp = event.timestamp.strftime("%Y-%m-%d %H:%M:%S") if event.timestamp else "-"
            table.add_row(timestamp, event.event_type, event.device_id[:12], confidence, read_status)
        
        console.print(table)

    async def cmd_config_show(self, device_id: str):
        config = await self.db.get_device_config(device_id)
        if not config:
            console.print(f"[yellow]No config found for device {device_id}[/yellow]")
            return
        
        console.print(f"\n[bold]Configuration for {device_id}:[/bold]")
        console.print(f"  Sensitivity:     {config.sensitivity}")
        console.print(f"  Min Duration:    {config.min_duration}s")
        console.print(f"  Cooldown:        {config.cooldown}s")
        console.print(f"  Record Duration: {config.record_duration}s")
        console.print(f"  Retention Days:  {config.retention_days}")
        console.print(f"  Screen Timeout:  {config.screen_timeout}s")

    async def cmd_config_update(self, device_id: str, **kwargs):
        from ..models.models import DeviceConfig
        
        current = await self.db.get_device_config(device_id)
        if not current:
            current = DeviceConfig()
        
        updated = DeviceConfig(
            sensitivity=kwargs.get('sensitivity', current.sensitivity),
            min_duration=kwargs.get('min_duration', current.min_duration),
            cooldown=kwargs.get('cooldown', current.cooldown),
            record_duration=kwargs.get('record_duration', current.record_duration),
            retention_days=kwargs.get('retention_days', current.retention_days),
            screen_timeout=kwargs.get('screen_timeout', current.screen_timeout)
        )
        
        await self.db.save_device_config(device_id, updated)
        console.print(f"[green]Configuration updated for {device_id}[/green]")

    async def cmd_server_status(self):
        console.print("\n[bold]Smart Doorbell Server Status[/bold]")
        console.print("  HTTP API:  http://0.0.0.0:8000")
        console.print("  WebSocket: ws://0.0.0.0:8080")
        console.print("  Web UI:    http://0.0.0.0:8000")


def create_cli(db, ws_client=None):
    return CLI(db, ws_client)
