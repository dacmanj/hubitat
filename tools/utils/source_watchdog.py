from __future__ import annotations
from watchdog.events import FileSystemEventHandler
from watchdog.observers import Observer
from typing import TYPE_CHECKING
if TYPE_CHECKING:
    from .file import HubitatFileManager
import os


class WatchdogModificationHandler(FileSystemEventHandler):
    def __init__(self, hfm: HubitatFileManager):
        self.hfm = hfm
        super().__init__()

    def on_modified(self, event):
        if event.event_type == 'modified':
            src_path = os.path.relpath(event.src_path)
            [self.hfm.update_latest_version(f) for f in self.hfm.targets.apps if f.source_path == src_path]
            [self.hfm.update_latest_version(f) for f in self.hfm.targets.drivers if f.source_path == src_path]


def watch_folder_for_changes(path, hfm: HubitatFileManager):
    event_handler = WatchdogModificationHandler(hfm)
    observer = Observer()
    observer.schedule(event_handler, path, recursive=True)
    observer.start()
    return observer