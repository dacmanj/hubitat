 /**
 * Moen Flo Manager for Hubitat By David Manuel
 * Licensed under CC BY 4.0 see https://creativecommons.org/licenses/by/4.0
 * Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF 
 * ANY KIND, either express or implied. See the License for the specific language governing permissions and 
 * limitations under the License.
 */

metadata {
    definition (name: "Moen FLO Smart Shutoff", namespace: "dacmanj", author: "David Manuel") {
        capability "Valve"
        capability "TemperatureMeasurement"
        capability "SignalStrength"
        capability "PressureMeasurement"
        capability "LiquidFlowRate"
        capability "Polling"

        command "reset"
        command "manualHealthTest"

        attribute "updated", "string"
        attribute "ssid", "string"
        attribute "lastHubitatHealthtestStatus", "string"
        attribute "lastEvent", "string"
        attribute "lastEventDetail", "string"
        attribute "lastEventDateTime", "string"
        attribute "lastHealthTestStatus", "string"
        attribute "lastHealthTestDetail", "string"
        attribute "lastHealthTestDateTime", "string"

    }

}

def open() {
    valveUpdate("open")
}

def reset() {
    state.clear()
    unschedule()
    configure()
}

def updated() {
    configure()
    if (state.configured) poll()
}

def installed() {
    log.debug "start device installed()"
    configure()
}

def unschedulePolling() {
    unschedule()
}

def schedulePolling() {
    unschedule(poll)
    if (parent?.pollingInterval) {
        schedule(parent.getCronString(), poll)
    }
}

def poll() {
    if (parent?.logEnable) log.debug("Polling Moen")
    getDeviceInfo()
    getLastAlerts()
    getHealthTestInfo()
    parent.updateDeviceAndAppName()
}

def close() {
    valveUpdate("closed")
}

def valveUpdate(target) {
    def deviceId = device.getDataValue("deviceId")
    def uri = "/devices/${deviceId}"
    if (parent?.logEnable) log.debug "Updating valve status to ${target}"
    def body = [valve:[target: target]]
    def response = parent.makeAPIPost(uri, body, "Valve Update")
    sendEvent(name: "valve", value: response?.data?.valve?.target)
}


def getDeviceInfo() {
    def deviceId = device.deviceNetworkId.substring(0,36)
    if (parent?.logEnable) log.debug "Getting device data for: ${deviceId}"
    def deviceInfo = parent.getDeviceData(deviceId)
    def location = parent.getLocationsCache()[deviceInfo?.location?.id]
    device.updateDataValue("locationNickname", location?.nickname)
    device.updateDataValue("locationAddress", location?.address)
    device.updateDataValue("deviceId", deviceInfo?.id)
    device.updateDataValue("locationId", deviceInfo?.location?.id)
    device.updateDataValue("deviceNickname", deviceInfo?.nickname)
    device.updateDataValue("deviceType", deviceInfo?.deviceType)
    device.updateDataValue("deviceModel", deviceInfo?.deviceModel)
    device.updateDataValue("firmwareVersion", deviceInfo?.fwVersion)
    flowrate = deviceInfo?.telemetry?.current?.gpm
    pressure = deviceInfo?.telemetry?.current?.psi
    if (parent.getUnits() == "metric") {
        flowrate = round(flowrate * 3.785411784, 2)
        pressure = round(pressure * 6.89475729, 2)
        sendEvent(name: "rate", value: flowrate, unit: "LPM")
        sendEvent(name: "pressure", value: pressure, unit: "kPa")
    }

    if (parent.getUnits() == "imperial"){
        flowrate = round(flowrate, 2)
        pressure = round(pressure, 2)
        sendEvent(name: "rate", value: flowrate, unit: "GPM")
        sendEvent(name: "pressure", value: pressure)
    }
    def deviceTemperature = deviceInfo?.telemetry?.current?.tempF
    if (deviceTemperature > 150) {
        deviceTemperature = deviceTemperature / 3
    }
    if (parent.getUnits() == "imperial") {
        sendEvent(name: "temperature", value: deviceTemperature, unit: "F")
    } else {
        deviceTemperature = round(fahrenheitToCelsius(deviceTemperature), 2)
        sendEvent(name: "temperature", value: deviceTemperature, unit: "C")
    }
    
    sendEvent(name: "updated", value: deviceInfo?.telemetry?.current?.updated)
    sendEvent(name: "valve", value: deviceInfo?.valve?.target)
    sendEvent(name: "rssi", value: deviceInfo?.connectivity?.rssi)
    sendEvent(name: "ssid", value: deviceInfo?.connectivity?.ssid)
}

def getLastAlerts() {
    def deviceId = device.getDataValue("deviceId")
    def data = parent.getLastDeviceAlert(deviceId)
    if (data) {
        sendEvent(name: "lastEvent", value: data[0]?.displayTitle)
        sendEvent(name: "lastEventDetail", value: data[0].displayMessage)
        sendEvent(name: "lastEventDateTime", value: data[0].createAt)
        for (alert in data) {
            if (alert?.healthTest?.roundId) {
                sendEvent(name: "lastHealthTestStatus", value: alert.displayTitle)
                sendEvent(name: "lastHealthTestDetail", value: alert.displayMessage)
                sendEvent(name: "lastHealthTestDateTime", value: alert.createAt)
                break;
            }
        }
    }
}

def round(d, places = 2) {
    if (d || d == 0) {
        return (d as double).round(places)
    }
    else {
        return d
    }
}

def getHealthTestInfo() {
    def lastHealthTestId = device.getDataValue("lastHubitatHealthtestId")
    def deviceId = device.getDataValue("deviceId")
    def uri = "/devices/${deviceId}/healthTest/${lastHealthTestId}"
    if(lastHealthTestId && lastHealthTestId != "") {
        def response = parent.makeAPIGet(uri, "Get Last Hubitat HealthTest Info")
        sendEvent(name: "lastHubitatHealthtestStatus", value: response?.data?.status)
        if (!response?.data?.status) {
            device.removeDataValue("lastHubitatHealthtestId")
        }
    } else {
        if (parent?.logEnable) log.info "Skipping Healthtest Update: No Hubitat Health Test Id Found"
    }
}

def manualHealthTest() {
    def deviceId = device.getDataValue("deviceId")
    def uri = "/devices/${deviceId}/healthTest/run"
    def response = parent.makeAPIPost(uri, "", "Manual Health Test")
    def roundId = response?.data?.roundId
    def created = response?.data?.created
    def status = response?.data?.status
    device.updateDataValue("lastHubitatHealthtest", created)
    device.updateDataValue("lastHubitatHealthtestId", roundId)
    sendEvent(name: "lastHubitatHealthtestStatus", value: status)
}

def configure() {
    getDeviceInfo()
    schedulePolling()
    state.configured = true
}

