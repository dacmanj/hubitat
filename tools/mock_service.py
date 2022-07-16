from flask import Flask
import json
import time

app = Flask(__name__)

USER_EMAIL = 'david@example.com'
USER_ID = 'f115d8b6-c72e-4b7d-b568-1eda559f3975'

@app.route('/')
def hello():
    return 'Hello, World!'

# todo: mock JWT user id
@app.route('/api/v1/users/auth', methods=['POST', 'GET'])
def auth():
    with open('data/auth.json', 'r') as f:
        auth_resp = json.loads("\n".join(f.readlines()))
    auth_resp['timeNow'] = int(time.time())
    auth_resp['tokenPayload']['timestamp'] = int(time.time())
    auth_resp['tokenPayload']['user']['user_id'] = USER_ID
    auth_resp['tokenPayload']['user']['email'] = USER_EMAIL

    return auth_resp

@app.route('/api/v2/locations/<location_id>', methods=['GET'])
def locations(location_id):
    with open('data/locations.json', 'r') as f:
        locations = json.loads("\n".join(f.readlines()))
    return locations

