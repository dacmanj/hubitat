package moenflomanager

import me.biocomp.hubitat_ci.api.common_api.DeviceWrapper
import me.biocomp.hubitat_ci.api.common_api.Log
import me.biocomp.hubitat_ci.api.device_api.DeviceExecutor
import me.biocomp.hubitat_ci.device.HubitatDeviceSandbox
import me.biocomp.hubitat_ci.device.HubitatDeviceScript
import me.biocomp.hubitat_ci.validation.Flags
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Exercises the pure conversion/parsing logic in MoenSmartShutoff.groovy through hubitat_ci,
 * which loads the real driver file and mocks out the Hubitat platform (log/device/sendEvent/parent).
 */
class MoenSmartShutoffSpec extends Specification {
    private static final File DRIVER_FILE = new File("MoenFloManager/drivers/MoenSmartShutoff.groovy")
    private static final String DEVICE_ID = "11111111-1111-1111-1111-111111111111"

    List<Map> sentEvents
    Map<String, String> dataValues
    FakeParent parent
    DeviceWrapper device

    def setup() {
        sentEvents = []
        dataValues = [:]
        parent = new FakeParent()

        device = Mock(DeviceWrapper) {
            _ * getDeviceNetworkId() >> "${DEVICE_ID}-1"
            _ * getDataValue(_) >> { String key -> dataValues[key] }
            _ * updateDataValue(_, _) >> { String key, String value -> dataValues[key] = value }
        }
    }

    private HubitatDeviceScript runDriver() {
        DeviceExecutor api = Mock(DeviceExecutor) {
            _ * getLog() >> Stub(Log)
            _ * getDevice() >> device
            _ * sendEvent(_) >> { Map event -> sentEvents << event }
            _ * fahrenheitToCelsius(_) >> { BigDecimal f -> (f - 32) * 5 / 9 }
        }

        new HubitatDeviceSandbox(DRIVER_FILE).run(
                api: api,
                parent: parent,
                // DontValidateCapabilities: hubitat_ci's capability table predates Hubitat's
                // "LiquidFlowRate" capability, so it would otherwise reject a valid driver.
                // AllowWritingToSettings: getDeviceInfo() assigns to the undeclared `flowrate`/
                // `pressure` locals, which Hubitat treats as script bindings, not settings.
                validationFlags: [Flags.DontRequireParseMethodInDevice, Flags.DontValidateCapabilities,
                                   Flags.AllowWritingToSettings])
    }

    private Object eventValue(String name) {
        sentEvents.find { it.name == name }?.value
    }

    @Unroll
    def "round(#input, #places) == #expected"() {
        expect:
            runDriver().round(input, places) == expected

        where:
            input   | places || expected
            null    | 2      || null
            0       | 2      || 0
            3.14159 | 2      || 3.14
            4.126   | 2      || 4.13
    }

    def "getDeviceInfo reports imperial units without converting"() {
        given:
            parent.units = "imperial"
            parent.deviceData = [
                    id        : "dev-1",
                    nickname  : "Kitchen Valve",
                    deviceType: "flo_device_v2",
                    deviceModel: "flo_device_075_v2",
                    fwVersion : "1.2.3",
                    location  : [id: "loc-1"],
                    telemetry : [current: [gpm: 2.5, psi: 55.12, tempF: 68.4, updated: "2026-08-20T10:00:00Z"]],
                    valve     : [target: "open"],
                    connectivity: [rssi: -50, ssid: "MyWifi"],
            ]
            parent.locationsCache = ["loc-1": [nickname: "Home", address: "123 Main St"]]

        when:
            def script = runDriver()
            script.getDeviceInfo()

        then:
            eventValue("rate") == 2.5
            eventValue("pressure") == 55.12
            eventValue("temperature") == 68.4
            sentEvents.find { it.name == "temperature" }.unit == "F"
            eventValue("valve") == "open"
            eventValue("rssi") == -50
            eventValue("ssid") == "MyWifi"
            dataValues["deviceNickname"] == "Kitchen Valve"
            dataValues["locationNickname"] == "Home"
            dataValues["locationAddress"] == "123 Main St"
    }

    def "getDeviceInfo converts to metric and corrects the firmware over-reported temperature"() {
        given:
            parent.units = "metric"
            parent.deviceData = [
                    telemetry: [current: [gpm: 1.0, psi: 1.0, tempF: 300, updated: "2026-08-20T10:00:00Z"]],
                    valve    : [target: "closed"],
            ]

        when:
            def script = runDriver()
            script.getDeviceInfo()

        then:
            // firmware occasionally reports tempF inflated 3x; driver divides it back down before converting
            eventValue("rate") == 3.79 // 1.0 gpm -> L/min
            eventValue("pressure") == 6.89 // 1.0 psi -> kPa
            eventValue("temperature") == 37.78 // (300/3 = 100F) -> C
            sentEvents.find { it.name == "temperature" }.unit == "C"
    }

    def "getLastAlerts surfaces the latest event and the latest health test result separately"() {
        given:
            dataValues["deviceId"] = "dev-1"
            parent.lastAlerts = [
                    [displayTitle: "Valve Closed", displayMessage: "Valve was closed due to a leak", createAt: "2026-08-20T09:00:00Z"],
                    [displayTitle: "Health Test Passed", displayMessage: "Health test completed successfully",
                     createAt: "2026-08-19T09:00:00Z", healthTest: [roundId: "abc123"]],
            ]

        when:
            def script = runDriver()
            script.getLastAlerts()

        then:
            eventValue("lastEvent") == "Valve Closed"
            eventValue("lastEventDetail") == "Valve was closed due to a leak"
            eventValue("lastEventDateTime") == "2026-08-20T09:00:00Z"
            eventValue("lastHealthTestStatus") == "Health Test Passed"
            eventValue("lastHealthTestDetail") == "Health test completed successfully"
            eventValue("lastHealthTestDateTime") == "2026-08-19T09:00:00Z"
    }

    /** Minimal duck-typed stand-in for MoenSmartShutoffInstance (the parent app instance). */
    static class FakeParent {
        boolean logEnable = false
        String units = "imperial"
        Map deviceData = [:]
        Map locationsCache = [:]
        List lastAlerts = null

        def getUnits() { units }
        def getDeviceData(deviceId) { deviceData }
        def getLocationsCache() { locationsCache }
        def getLastDeviceAlert(deviceId) { lastAlerts }
    }
}
