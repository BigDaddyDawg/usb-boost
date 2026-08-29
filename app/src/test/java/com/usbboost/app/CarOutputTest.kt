package com.usbboost.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarOutputTest {
    @Test
    fun intellilinkBluetoothIsCarEvenWithoutTheWordCar() {
        val state = CarOutput.classify(
            listOf(
                AudioSink(CarOutput.TYPE_BUILTIN_SPEAKER, "Speaker"),
                AudioSink(CarOutput.TYPE_BLUETOOTH_A2DP, "Vauxhall IntelliLink")
            ),
            usbCable = false,
            usbAccessory = false
        )
        assertEquals("Vauxhall IntelliLink", state.label)
        assertTrue(state.carLikely)
        assertTrue(
            BoostLogic.shouldApplyEffects(enabled = true, autoCarMode = true, carActive = state.carLikely)
        )
    }

    @Test
    fun intellilinkOnAndroidAutoBusIsUsbCar() {
        val state = CarOutput.classify(
            listOf(
                AudioSink(CarOutput.TYPE_BUILTIN_SPEAKER, "Speaker"),
                AudioSink(CarOutput.TYPE_BUS, "Vauxhall IntelliLink")
            ),
            usbCable = false,
            usbAccessory = false
        )
        assertEquals(OutputKind.USB, state.kind)
        assertEquals("Vauxhall IntelliLink", state.label)
        assertTrue(state.carLikely)
    }

    @Test
    fun usbDacNamedIntellilinkBeatsBluetooth() {
        val state = CarOutput.classify(
            listOf(
                AudioSink(CarOutput.TYPE_BLUETOOTH_A2DP, "Vauxhall IntelliLink"),
                AudioSink(CarOutput.TYPE_USB_DEVICE, "Vauxhall IntelliLink"),
                AudioSink(CarOutput.TYPE_BUILTIN_SPEAKER, "Speaker")
            ),
            usbCable = false,
            usbAccessory = false
        )
        assertEquals(OutputKind.USB, state.kind)
        assertEquals("Vauxhall IntelliLink", state.label)
        assertTrue(state.carLikely)
    }

    @Test
    fun androidAutoBusPlusBluetoothStillCountsAsCar() {
        val state = CarOutput.classify(
            listOf(
                AudioSink(CarOutput.TYPE_BUS, "bus"),
                AudioSink(CarOutput.TYPE_BLUETOOTH_A2DP, "Vauxhall IntelliLink"),
                AudioSink(CarOutput.TYPE_BUILTIN_SPEAKER, "Speaker")
            ),
            usbCable = false,
            usbAccessory = false
        )
        assertTrue(state.carLikely)
        assertEquals("Vauxhall IntelliLink", state.label)
        assertEquals(OutputKind.USB, state.kind)
    }

    @Test
    fun airpodsAreNotACar() {
        val state = CarOutput.classify(
            listOf(
                AudioSink(CarOutput.TYPE_BUILTIN_SPEAKER, "Speaker"),
                AudioSink(CarOutput.TYPE_BLUETOOTH_A2DP, "AirPods Pro")
            ),
            usbCable = false,
            usbAccessory = false
        )
        assertEquals("AirPods Pro", state.label)
        assertFalse(state.carLikely)
        assertEquals(OutputKind.BLUETOOTH, state.kind)
        assertFalse(
            BoostLogic.shouldApplyEffects(enabled = true, autoCarMode = true, carActive = state.carLikely)
        )
    }

    @Test
    fun speakerOnlyIsWaitingForCarWhenAutoCarIsOn() {
        val state = CarOutput.classify(
            listOf(AudioSink(CarOutput.TYPE_BUILTIN_SPEAKER, "Speaker")),
            usbCable = false,
            usbAccessory = false
        )
        assertEquals(OutputKind.PHONE, state.kind)
        assertFalse(state.carLikely)
        assertEquals("Phone speaker", state.label)
    }

    @Test
    fun usbAccessoryCountsAsCarEvenWithoutAnAudioDeviceName() {
        val state = CarOutput.classify(
            listOf(AudioSink(CarOutput.TYPE_BUILTIN_SPEAKER, "Speaker")),
            usbCable = false,
            usbAccessory = true
        )
        assertTrue(state.carLikely)
        assertEquals(OutputKind.USB, state.kind)
    }

    @Test
    fun knownHeadUnitNamesAreCar() {
        listOf("Ford SYNC", "Toyota Entune", "Chevrolet MyLink", "Android Auto", "MIB2").forEach { name ->
            assertTrue(name, CarOutput.isCarHeadUnitName(name))
        }
        assertFalse(CarOutput.isCarHeadUnitName("Pixel Buds"))
        assertFalse(CarOutput.isCarHeadUnitName("WH-1000XM5"))
    }

    @Test
    fun vauxhallTypoStillMatchesIntellilink() {
        assertTrue(CarOutput.isCarHeadUnitName("Vauxhill IntelliLink"))
        assertTrue(CarOutput.isCarHeadUnitName("Opel IntelliLink"))
    }
}
