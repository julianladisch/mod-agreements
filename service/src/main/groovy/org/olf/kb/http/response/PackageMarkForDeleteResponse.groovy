package org.olf.kb.http.response

import org.olf.kb.http.response.DeletionCounts
import org.olf.kb.http.response.MarkForDeleteResponse

class PackageMarkForDeleteResponse {
  Map<String, MarkForDeleteResponse> packages = [:]
  DeletionCounts statistics = new DeletionCounts()
}
