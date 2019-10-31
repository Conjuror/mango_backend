package org.mozilla.msrp.platform.vertical.content.db

import org.mozilla.msrp.platform.vertical.content.data.Category

class PublishDoc(
        var publish_timestamp: Long? = null,    // null if not ready for publish
        var category: String? = null,
        var locale: String? = null,
        var tag: String? = null,
        var created_timestamp: Long? = null,
        var data: Category? = null)
