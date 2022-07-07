from dotenv import load_dotenv
import sys
import os
import requests
from enum import Enum, auto
import webbrowser
load_dotenv()

import time
import logging
from watchdog.observers import Observer
from watchdog.events import LoggingEventHandler


class CodeType(Enum):
    APP = "app"
    DRIVER = "driver"


class bcolors:
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKCYAN = '\033[96m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    UNDERLINE = '\033[4m'


STATUS_COLOR = {
    "success": bcolors.OKGREEN,
    "error": bcolors.FAIL
}


"""WARNING!!! You must update these ids and set hubitat ip address in a .env file to use this"""
TARGETS = {
    "MoenFloManager": {
        "apps": [
            {"id": 827, "source_path": "MoenFloManager/apps/MoenDeviceManager.groovy"},
            {"id": 828, "source_path": "MoenFloManager/apps/MoenLocationInstance.groovy"},
            {"id": 829, "source_path": "MoenFloManager/apps/MoenSmartShutoffInstance.groovy"},
            {"id": 830, "source_path": "MoenFloManager/apps/MoenSmartWaterDetectorInstance.groovy"}
        ],
        "drivers": [
            {"id": 1604, "source_path": "MoenFloManager/drivers/MoenLocation.groovy"},
            {"id": 1606, "source_path": "MoenFloManager/drivers/MoenSmartShutoff.groovy"},
            {"id": 1605, "source_path": "MoenFloManager/drivers/MoenSmartWaterDetector.groovy"}
        ]
    }
}

ip = os.getenv('HUBITAT')
base_url = f"http://{ip}"


def update_package(target_name, apps=True, drivers=True):
    print(f"""{bcolors.HEADER}STARTING UPLOAD{bcolors.ENDC}""")
    return do_on_package(update_latest_version, target_name, apps, drivers)


def retrieve_package(target_name, apps=True, drivers=True):
    print(f"""{bcolors.HEADER}STARTING RETRIEVE{bcolors.ENDC}""")
    return do_on_package(retrieve_latest_source, target_name, apps, drivers)


def do_on_package(op_func, target_name, apps=True, drivers=True):
    target = TARGETS.get(target_name)
    if apps:
        for app in target.get("apps"):
            op_func(app['id'], app['source_path'], CodeType.APP.value)

    if drivers:
        for driver in target.get('drivers'):
            op_func(driver['id'], driver['source_path'], CodeType.DRIVER.value)


def update_latest_version(id, source_path, type):
    info = get_info(id, type)[0]
    current_version = info.get('version')
    name = info.get('name')
    display_id_name = ' '.join([str(x) for x in [id, name] if x])
    source = get_source(source_path)
    if source == info.get('source'):
        print(f"{bcolors.OKCYAN}SKIPPED{bcolors.ENDC}: {type.title()} {display_id_name} v{current_version} is the same as {source_path}")
    else:
        print(f"{bcolors.BOLD}UPDATING:{bcolors.ENDC} {type.title()} {display_id_name} v{current_version} with {source_path}")
        result = update_code(id, source, current_version, type)
        status_code = result[1].status_code
        result_json = result[0]
        new_version = result_json.get('version')
        status = result_json['status']
        status_color = STATUS_COLOR.get(status)
        print(f"{status_color}{status.upper()}{bcolors.ENDC}: Update of {type.title()} {display_id_name} v{new_version} {status_code}:")
        if status == 'error':
            error_message = result_json.get('errorMessage').replace('\n', '')
            print(f"\t{status_color}{source_path}{bcolors.ENDC}")
            print(f"\t\t{status_color}{error_message}{bcolors.ENDC}") 
        return result


def retrieve_latest_source(id, source_path, type):
    info = get_info(id, type)[0]
    current_version = info.get('version')
    name = info.get('name')
    display_id_name = ' '.join([str(x) for x in [id, name] if x])
    source = get_source(source_path)
    if source == info.get('source'):
        print(f"{type.title()} {display_id_name} v{current_version} is the same as {source_path}: SKIPPED")
    else:
        print(f"Updating {source_path} with {type.title()} {display_id_name} v{current_version} fom Hubitat")
        save_source(source_path, info.get('source'))


def get_source(source_path):
    with open(source_path) as f:
        source_code = f.read()
    return source_code


def save_source(source_path, source):
    with open(source_path, "w") as f:
        source_code = f.write(source)
    return source_code


def update_code(id, source_code, version, type):
    url = f"{base_url}/{type}/ajax/update"
    body = {
        "id": id,
        "source": source_code,
        "version": version
    }
    r = requests.post(url, data=body)
    return r.json(), r


def get_info(id, type):
    url = f"{base_url}/{type}/ajax/code?id={id}"
    r = requests.get(url)
    return r.json(), r


def open_all(pkg):
    for app in TARGETS.get(pkg)['apps']:
        open_in_hubitat_code_editor(CodeType.APP, app['id'])
    for driver in TARGETS.get(pkg)['drivers']:
        open_in_hubitat_code_editor(CodeType.DRIVER, driver['id'])


def open_in_hubitat_code_editor(type, id):
    url = f"""{base_url}/{type.value}/editor/{id}"""
    webbrowser.open(url)


if len(sys.argv) > 1:
    package_name = sys.argv[1]
    direction = 'upload' if len(sys.argv) == 3 and sys.argv[2] == 'upload' else 'retrieve'
else:
    package_name = os.getenv('TARGET')
    direction = os.getenv('DIRECTION')
    auto_upload = os.getenv('AUTOUPLOAD')


from watchdog.events import FileSystemEventHandler

class WatchdogModificationHandler(FileSystemEventHandler):
    def on_modified(self, event):
        if event.event_type == 'modified':
            package_data = TARGETS.get(package_name)
            src_path = os.path.relpath(event.src_path)
            app_modified = [update_latest_version(x['id'], src_path, 'app') for x in package_data['apps'] if x.get('source_path') == src_path]
            driver_modified = [update_latest_version(x['id'], src_path, 'driver') for x in package_data['drivers'] if x.get('source_path') == src_path]

def auto_upload():
    print(f"""{bcolors.HEADER}STARTING AUTO-UPLOAD{bcolors.ENDC}""")
    logging.basicConfig(level=logging.INFO,
                        format='%(asctime)s - %(message)s',
                        datefmt='%Y-%m-%d %H:%M:%S')
    event_handler = WatchdogModificationHandler()
    observer = Observer()
    path = '.'
    observer.schedule(event_handler, path, recursive=True)
    observer.start()
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        observer.stop()
    observer.join()


if __name__ == "__main__":
    if not package_name:
        print("Warning! you must update the app id/driver ids to match your device.")
    else:
        if direction == 'upload':
            print("direction upload")
            if auto_upload:
                print("starting auto upload")
                auto_upload()
            else:
                update_package(package_name)
        else:
            retrieve_package(package_name)

