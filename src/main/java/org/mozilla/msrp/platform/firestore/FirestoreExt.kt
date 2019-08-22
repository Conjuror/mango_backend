package org.mozilla.msrp.platform.firestore

import com.google.cloud.firestore.DocumentSnapshot

fun DocumentSnapshot.areFieldsPresent(fieldNames: List<String>): Boolean {
    fieldNames.forEach { fieldName ->
        this.get(fieldName) ?: return false
    }
    return true
}