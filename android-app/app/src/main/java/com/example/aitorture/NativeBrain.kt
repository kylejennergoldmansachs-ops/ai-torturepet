package com.example.aitorture

object NativeBrain {
    init { System.loadLibrary("reservoir") }

    external fun nativeInit(neuronCount: Int, fanout: Int): Boolean
    external fun nativeApplyInputs(arr: FloatArray)
    external fun nativeStep()
    external fun nativeExportSummary(): String
}
