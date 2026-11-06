package com.hubops.vislumbrops.reporting

import com.hubops.vislumbrops.core.VerificationContract

data class VislumbropsReport(
    val contract: VerificationContract,
    val htmlReport: String,
    val jsonReport: String,
    val markdownSummary: String
)
