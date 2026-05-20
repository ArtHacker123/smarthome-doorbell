#!/usr/bin/env python3
"""
Smart Doorbell PC Server - CLI Entry Point
"""
import asyncio
import sys
import logging
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

from server.main import DoorbellServer
from server.cli.commands import create_cli

_log_format = '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
_log_formatter = logging.Formatter(_log_format)

_console_handler = logging.StreamHandler(sys.stdout)
_console_handler.setFormatter(_log_formatter)

_file_handler = logging.FileHandler(Path(__file__).parent / "server.log", mode="a", encoding="utf-8")
_file_handler.setFormatter(_log_formatter)

_root_logger = logging.getLogger()
_root_logger.setLevel(logging.INFO)
_root_logger.addHandler(_console_handler)
_root_logger.addHandler(_file_handler)

logger = logging.getLogger(__name__)


async def main():
    if len(sys.argv) > 1:
        command = sys.argv[1]
        
        from server.core.config import get_config
        from server.models.models import Database
        
        config = get_config().server
        db = Database(config.database_url)
        await db.init_db()
        
        cli = create_cli(db)
        
        if command == "devices":
            await cli.cmd_devices_list()
        elif command == "events":
            device_id = sys.argv[2] if len(sys.argv) > 2 else None
            await cli.cmd_events_list(device_id)
        elif command == "config":
            if len(sys.argv) > 2:
                device_id = sys.argv[2]
                await cli.cmd_config_show(device_id)
            else:
                print("Usage: doorbell config <device_id>")
        else:
            print(f"Unknown command: {command}")
            print("Available commands: devices, events, config")
    else:
        server = DoorbellServer()
        try:
            await server.start()
        except KeyboardInterrupt:
            logger.info("Shutting down...")
            await server.stop()


if __name__ == "__main__":
    asyncio.run(main())
