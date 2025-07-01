package org.olf.kb.http.response

// Store markForDelete data
class MarkForDeletionGroup {
  MarkForDeleteMap resourceIds = new MarkForDeleteMap()
  DeletionCounts statistics = new DeletionCounts()
}

// Store deletion data
class DeletedGroup {
  MarkForDeleteMap resourceIds = new MarkForDeleteMap()
  DeletionCounts statistics = new DeletionCounts()
}

// The final response object
class DeleteResponse {
  MarkForDeletionGroup markedForDeletion = new MarkForDeletionGroup()
  DeletedGroup deleted = new DeletedGroup()
}

// Store the number of resources for markForDelete or delete methods.
class DeletionCounts {
  Integer pci;
  Integer pti;
  Integer ti;
  Integer work;

  DeletionCounts() {
    this.pci = 0
    this.pti = 0
    this.ti = 0
    this.work = 0
  }

  DeletionCounts(Integer pci, Integer pti, Integer ti, Integer work) {
    this.pci = pci
    this.pti = pti
    this.ti = ti
    this.work = work
  }

  @Override
  public String toString() {
    return "DeletionCounts{" +
      "pciDeleted=" + pci +
      ", ptiDeleted=" + pti +
      ", tiDeleted=" + ti +
      ", workDeleted=" + work +
      '}';
  }
}
