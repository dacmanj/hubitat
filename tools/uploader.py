from dotenv import load_dotenv
import sys
import os
import requests
load_dotenv()

from enum import Enum
class CodeType(Enum):
    APP = "app"
    DRIVER = "driver"

"""WARNING!!! You must update these ids and set put hubitat ip address in a .env file to use this"""
targets = {
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

ip = os.getenv('hubitat')
base_url = f"http://{ip}"


def update_package(target_name, apps=True, drivers=True):
    target = targets.get(target_name)
    if apps:
        for app in target.get("apps"):
            update_latest_version(app['id'], app['source_path'], CodeType.APP.value)

    if drivers:
        for driver in target.get('drivers'):
            update_latest_version(driver['id'], driver['source_path'], CodeType.DRIVER.value)


def update_latest_version(id, source_path, type):
    info = get_info(id, type)[0]
    current_version = info.get('version')
    name = info.get('name')
    display_id_name = ' '.join([str(x) for x in [id, name] if x])
    source = get_source(source_path)
    if source == info.get('source'):
        print(f"{type.title()} {display_id_name} v{current_version} is the same as {source_path}: SKIPPED")
    else:
        print(f"Updating {type.title()} {display_id_name} v{current_version} with {source_path}")
        result = update_code(id, source, current_version, type)
        status_code = result[1].status_code
        result_json = result[0]
        new_version = result_json.get('version')
        status = result_json.get('status')
        print(f"Update of {type.title()} {display_id_name} v{new_version} {status_code}: {status}")
        return result


def get_source(source_path):
    with open(source_path) as f:
        source_code = f.read()
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

if len(sys.argv) > 1:
    target = sys.argv[1]
else:
    target = os.getenv('TARGET')

if __name__ == "__main__":
    if not target:
        print("Warning! you must update the app id/driver ids to match your device.")
        update_package('MoenFloManager')
    else:
        update_package(target)
