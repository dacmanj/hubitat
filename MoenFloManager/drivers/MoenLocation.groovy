/**
    FLO by Moen for Hubitat by David Manuel is licensed under CC BY 4.0 see https://creativecommons.org/licenses/by/4.0
    Software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
    ANY KIND, either express or implied. See the License for the specific language governing permissions and
    limitations under the License.

    v2.0.0   2021-10-03    Forked standalone driver moved API and configuration to App

 */

metadata {
    definition (name: "Moen FLO Location", namespace: "dacmanj", author: "David Manuel", importUrl: "https://raw.githubusercontent.com/dacmanj/hubitat/main/MoenFloManager/drivers/FloByMoenLocation.groovy") {
        capability "PushableButton"
        capability "LocationMode"
        capability "Momentary"

        command "home"
        command "away"
        command "sleepMode"
        command "reset"
        command "pollMoen"

        attribute "numberOfButtons", "number"
        attribute "pushed", "number"
        attribute "mode", "enum", ["home","away","sleep"]
        attribute "updated", "string"
        attribute "totalGallonsToday", "number"
        attribute "lastEvent", "string"
        attribute "lastEventDetail", "string"
        attribute "lastEventDateTime", "string"

    }

}

import groovy.transform.Field
@Field final String baseUrl = 'https://api-gw.meetflo.com/api/v2'

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
    if (parent?.pollingInterval) {
        schedule("0 0/${parent?.pollingInterval} * 1/1 * ? *", pollMoen)
    }
}

def pollMoen() {
    if (parent?.logEnable) log.debug("Polling Moen")
    getLocationInfo()
    getConsumption()
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
    if (mode == "sleep") {
        body.put("revertMinutes", parent?.revertMinutes)
        body.put("revertMode", parent?.revertMode)
    }
    def response = parent.makeAPIPost(uri, body, "Mode Update", [204])
    sendEvent(name: "mode", value: mode)
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


def getLocationInfo() {
    def locationId = device.deviceNetworkId.substring(0,36)
    if (parent?.logEnable) log.debug "Getting location data for: ${locationId}"
    def location = parent.getLocationData(locationId)
    device.updateDataValue("locationNickname", location?.nickname)
    device.updateDataValue("locationAddress", location?.address)
    device.updateDataValue("locationId", locationId)
    def system_mode = location?.systemMode.target
    sendEvent(name: "mode", value: system_mode)
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

def configure() {
    getLocationInfo()
    sendEvent(name:"numberOfButtons", value: 3)
    schedulePolling()
    state.configured = true
}

