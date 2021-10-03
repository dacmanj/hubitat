/**
    FLO by Moen for Hubitat by David Manuel is licensed under CC BY 4.0 see https://creativecommons.org/licenses/by/4.0
    Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
    ANY KIND, either express or implied. See the License for the specific language governing permissions and
    limitations under the License.

    v2.0.0   2021-10-03    Forked standalone driver moved API and configuration to App

 */

metadata {
    definition (name: "FLO by Moen Water Valve", namespace: "dacmanj", author: "David Manuel", importUrl: "https://raw.githubusercontent.com/dacmanj/hubitat/main/MoenFloSmartShutoff/moenflo.groovy") {
        capability "Valve"
        capability "PushableButton"
        capability "LocationMode"
        capability "Momentary"
        capability "TemperatureMeasurement"
        capability "SignalStrength"

        command "home"
        command "away"
        command "sleepMode"
        command "reset"
        command "manualHealthTest"
        command "pollMoen"

        attribute "numberOfButtons", "number"
        attribute "pushed", "number"
        attribute "mode", "enum", ["home","away","sleep"]
        attribute "valve", "enum", ["open", "closed"]
        attribute "temperature", "number"
        attribute "gpm", "number"
        attribute "psi", "number"
        attribute "updated", "string"
        attribute "rssi", "number"
        attribute "ssid", "string"
        attribute "lastHubitatHealthtestStatus", "string"
        attribute "totalGallonsToday", "number"
        attribute "lastEvent", "string"
        attribute "lastEventDetail", "string"
        attribute "lastEventDateTime", "string"
        attribute "lastHealthTestStatus", "string"
        attribute "lastHealthTestDetail", "string"
        attribute "lastHealthTestDateTime", "string"

    }

}

import groovy.transform.Field
@Field final String baseUrl = 'https://api-gw.meetflo.com/api/v2'

def logsOff(){
    device.updateSetting("logEnable", false)
    log.warn "Debug Logging Disabled..."
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
    if (state.configured) pollMoen()
}

def installed() {
    log.debug "start device installed()"
    configure()
}

def unschedulePolling() {
    unschedule(pollMoen)
}

def schedulePolling() {
    unschedule(pollMoen)
    if (parent?.pollingInterval != "None") {
        schedule("0 0/${parent?.pollingInterval} * 1/1 * ? *", pollMoen)
    }
}

def pollMoen() {
    if (parent?.logEnable) log.debug("Polling Moen")
    getDeviceInfo()
    getHealthTestInfo()
    getConsumption()
    getLastAlerts()
}

def close() {
    valveUpdate("closed")
}

def home() {
    setMode("home")
}

def away() {
    setMode("away")
}

def sleepMode() {
    setMode("sleep")
}

def setMode(mode) {
    if (parent?.logEnable) log.debug "Setting Flo mode to ${mode}"
    def locationId = device.getDataValue("locationId")
    def uri = "${baseUrl}/locations/${locationId}/systemMode"
    def body = [target:mode]
    def headers = [:]
    headers.put("Content-Type", "application/json")
    headers.put("Authorization", device.getDataValue("token"))
    if (mode == "sleep") {
        body.put("revertMinutes", parent?.revertMinutes)
        body.put("revertMode", parent?.revertMode)
    }
    def response = parent.makeAPIPost(uri, body, "Mode Update", [204])
    sendEvent(name: "mode", value: mode)
}

def valveUpdate(target) {
    def deviceId = device.deviceNetworkId
    def uri = "${baseUrl}/devices/${deviceId}"
    if (parent?.logEnable) log.debug "Updating valve status to ${target}"
    def body = [valve:[target: target]]
    def response = parent.makeAPIPost(uri, body, "Valve Update")
    sendEvent(name: "valve", value: response?.data?.valve?.target)
}

def push(btn) {
    switch(btn) {
       case 1: mode = "home"; break;
       case 2: mode = "away"; break;
       case 3: mode = "sleep"; break;
       default: mode = "home";
    }
    if (parent?.logEnable) log.debug "Setting Flo mode to ${mode} via button press"
    setMode(mode)
}


def getDeviceInfo() {
    if (parent?.logEnable) log.debug "Getting device data for: ${device.deviceNetworkId}"
    def deviceId = device.deviceNetworkId
    def deviceInfo = parent.getDeviceData(deviceId)
    def location = parent.getLocationsCache()[deviceInfo?.location?.id]
    device.updateDataValue("locationNickname", location?.nickname)
    device.updateDataValue("locationAddress", location?.address)
    device.updateDataValue("deviceId", deviceInfo?.id)
    device.updateDataValue("locationId", deviceInfo?.location.id)
    device.updateDataValue("deviceNickname", deviceInfo?.nickname)
    device.updateDataValue("deviceType", deviceInfo?.deviceType)
    device.updateDataValue("deviceModel", deviceInfo?.deviceModel)
    device.updateDataValue("firmwareVersion", deviceInfo?.fwVersion)
    sendEvent(name: "gpm", value: round(deviceInfo?.telemetry?.current?.gpm))
    sendEvent(name: "psi", value: round(deviceInfo?.telemetry?.current?.psi))
    def deviceTemperature = deviceInfo?.telemetry?.current?.tempF
    if (deviceTemperature > 150) {
        deviceTemperature = deviceTemperature / 3
    }
    sendEvent(name: "temperature", value: deviceTemperature, unit: "F")
    sendEvent(name: "updated", value: deviceInfo?.telemetry?.current?.updated)
    sendEvent(name: "valve", value: deviceInfo?.valve?.target)
    sendEvent(name: "rssi", value: deviceInfo?.connectivity?.rssi)
    sendEvent(name: "ssid", value: deviceInfo?.connectivity?.ssid)
    def system_mode = deviceInfo?.fwProperties?.system_mode
    def SYSTEM_MODES = [2: "home", 3: "away", 5: "sleep"]
    sendEvent(name: "mode", value: SYSTEM_MODES[system_mode])
}

def getLastAlerts() {
    def deviceId = device.deviceNetworkId
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
    if (d) {
        return (d as double).round(places)
    }
    else {
        return d
    }
}

def getConsumption() {
    def locationId = device.getDataValue("locationId")
    def startDate = new Date().format('yyyy-MM-dd') + 'T00:00:00.000'
    def endDate = new Date().format('yyyy-MM-dd') + 'T23:59:59.999'
    def uri = "${baseUrl}/water/consumption?startDate=${startDate}&endDate=${endDate}&locationId=${locationId}&interval=1h"
    def response = parent.makeAPIGet(uri, "Get Consumption")
    def data = response.data
    sendEvent(name: "totalGallonsToday", value: round(data?.aggregations?.sumTotalGallonsConsumed))
}

def getHealthTestInfo() {
    def lastHealthTestId = device.getDataValue("lastHubitatHealthtestId")
    def deviceId = device.deviceNetworkId
    def uri = "${baseUrl}/devices/${deviceId}/healthTest/${lastHealthTestId}"
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
    def device_id = device.deviceNetworkId
    def uri = "${baseUrl}/devices/${deviceId}/healthTest/run"
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
    sendEvent(name:"numberOfButtons", value: 3)
    schedulePolling()
    state.configured = true
}

