package org.olf.kb.http.response

class MarkForDeleteMap {
  Set<String> pci;
  Set<String> pti;
  Set<String> ti;
  Set<String> work;

  MarkForDeleteMap(Set<String> pci, Set<String> pti, Set<String> ti, Set<String> work) {
    this.pci = pci
    this.pti = pti
    this.ti = ti
    this.work = work
  }

  MarkForDeleteMap() {
    this.pci = new HashSet<String>()
    this.pti = new HashSet<String>()
    this.ti = new HashSet<String>()
    this.work = new HashSet<String>()
  }


  @Override
  public String toString() {
    return "MarkForDeleteMap{ pci=${pci}, pti=${pti}, ti=${ti}, work=${work} }"
  }
}
