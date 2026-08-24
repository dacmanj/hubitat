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
 * Exercises MoenSmartWaterDetector.groovy through hubitat_ci, which loads the real driver
 * file and mocks out the Hubitat platform (log/device/sendEvent/parent).
 */
class MoenSmartWaterDetectorSpec extends Specification {
    private static final File DRIVER_FILE = new File("MoenFloManager/drivers/MoenSmartWaterDetector.groovy")

    List<Map> sentEvents
    Map<String, String> dataValues
    FakeParent parent
    DeviceWrapper device

    def setup() {
        sentEvents = []
        dataValues = [:]
        parent = new FakeParent()

        device = Mock(DeviceWrapper) {
            _ * getDeviceNetworkId() >> "22222222-2222-2222-2222-222222222222-1"
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
                validationFlags: [Flags.DontRequireParseMethodInDevice])
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
            0       | 2      || 0
            3.14159 | 2      || 3.14
            4.126   | 2      || 4.13
    }

    def "getDeviceInfo reports imperial units, rounding humidity/battery to whole numbers"() {
        given:
            parent.units = "imperial"
            parent.deviceData = [
                    id        : "dev-2",
                    nickname  : "Basement Detector",
                    location  : [id: "loc-1"],
                    telemetry : [current: [tempF: 70.4, humidity: 45.6, updated: "2026-08-20T10:00:00Z"]],
                    battery   : [level: 87.2],
                    fwProperties: [telemetry_water: true, telemetry_rssi: -60, wifi_sta_ssid: "MyWifi"],
            ]
            parent.locationsCache = ["loc-1": [nickname: "Basement", address: "123 Main St"]]

        when:
            def script = runDriver()
            script.getDeviceInfo()

        then:
            eventValue("temperature") == 70.4
            sentEvents.find { it.name == "temperature" }.unit == "F"
            eventValue("humidity") == 46
            eventValue("battery") == 87
            eventValue("water") == "wet"
            eventValue("rssi") == -60
            eventValue("ssid") == "MyWifi"
            dataValues["deviceNickname"] == "Basement Detector"
            dataValues["locationNickname"] == "Basement"
    }

    def "getDeviceInfo converts temperature to metric"() {
        given:
            parent.units = "metric"
            parent.deviceData = [
                    telemetry: [current: [tempF: 68, humidity: 50, updated: "2026-08-20T10:00:00Z"]],
                    battery  : [level: 100],
                    fwProperties: [telemetry_water: false],
            ]

        when:
            def script = runDriver()
            script.getDeviceInfo()

        then:
            eventValue("temperature") == 20 // (68F - 32) * 5/9 == 20C
            sentEvents.find { it.name == "temperature" }.unit == "C"
    }

    @Unroll
    def "getDeviceInfo maps the real boolean telemetry_water to water=#expected"() {
        given:
            parent.deviceData = [
                    telemetry: [current: [tempF: 68, humidity: 50, updated: "2026-08-20T10:00:00Z"]],
                    battery  : [level: 100],
                    fwProperties: [telemetry_water: rawValue],
            ]

        when:
            def script = runDriver()
            script.getDeviceInfo()

        then:
            eventValue("water") == expected

        where:
            rawValue || expected
            false    || "dry"
            true     || "wet"
    }

    /** Minimal duck-typed stand-in for MoenSmartWaterDetectorInstance (the parent app instance). */
    static class FakeParent {
        boolean logEnable = false
        String units = "imperial"
        Map deviceData = [:]
        Map locationsCache = [:]

        def getUnits() { units }
        def getDeviceData(deviceId) { deviceData }
        def getLocationsCache() { locationsCache }
    }
}
