/**
 * Moen Flo Water Detector for Hubitat 
 * Based on Moen Flo for Hubitat by David Manuel https://github.com/dacmanj/hubitat-moenflo
 * Licensed under CC BY 4.0 see https://creativecommons.org/licenses/by/4.0
 * Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF 
 * ANY KIND, either express or implied. See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 * 2020-09-17 v0.0.1 - Modified v0.1.07-alpha to support Moen Flo Water Detectors - Jeffrey Laughter
 * 
 */

metadata {
    definition (name: "Moen FLO Smart Water Detector", namespace: "dacmanj", author: "David Manuel, Jeffrey Laughter") {
        capability "RelativeHumidityMeasurement"
        capability "TemperatureMeasurement"
        capability "WaterSensor"
        capability "Battery"

        command "pollMoen"

        attribute "temperature", "number"
        attribute "humidity", "number"
        attribute "battery", "number"
        attribute "water", "enum", ["wet", "dry"]
        attribute "updated", "string"
        attribute "rssi", "number"
        attribute "ssid", "string"
        attribute "lastEvent", "string"
        attribute "lastEventDetail", "string"
        attribute "lastEventDateTime", "string"   
    }

}


def logout() {
    state.clear()
    unschedule()
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
    if (parent?.pollingInterval) {
        schedule("0 0/${parent?.pollingInterval} * 1/1 * ? *", pollMoen)
    }
}

def pollMoen() {
    if (logEnable) log.debug("Polling Moen")
    getDeviceInfo()
    getLastAlerts()
}

def getDeviceInfo() {
    def deviceId = device.deviceNetworkId.substring(0,36)
    if (parent?.logEnable) log.debug "Getting device data for: ${deviceId}"
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

    sendEvent(name: "temperature", value: round(deviceInfo?.telemetry?.current?.tempF, 0), unit: "F")
    sendEvent(name: "humidity", value: round(deviceInfo?.telemetry?.current?.humidity, 0), unit: "%")
    sendEvent(name: "battery", value: round(deviceInfo?.battery?.level, 0), unit: "%")
    def water_state = deviceInfo?.fwProperties?.telemetry_water
    def WATER_STATES = [1: "wet", 2: "dry"]
    if (water_state) {
        sendEvent(name: "water", value: WATER_STATES[1])}
    else { sendEvent(name: "water", value: WATER_STATES[2])}            
    sendEvent(name: "updated", value: deviceInfo?.telemetry?.current?.updated)
    sendEvent(name: "rssi", value: deviceInfo?.fwProperties?.telemetry_rssi)
    sendEvent(name: "ssid", value: deviceInfo?.fwProperties?.wifi_sta_ssid)


}


def getLastAlerts() {
    def deviceId = device.getDataValue("deviceId")
    def data = parent.getLastDeviceAlert(deviceId)
    if (data) {
        sendEvent(name: "lastEvent", value: data[0]?.displayTitle)
        sendEvent(name: "lastEventDetail", value: data[0].displayMessage)
        sendEvent(name: "lastEventDateTime", value: data[0].createAt)
    }
}

def round(d, places = 2) {
    try { return (d as double).round(places) }
    catch (Exception e) { return (null) }
}

def configure() {
    schedulePolling()
    state.configured = true
}