package com.k_int.accesscontrol.core;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class BasicAccessPolicy implements AccessPolicy{
  String id;
  String description;
  Instant dateCreated;
  Instant dateUpdated;
  AccessPolicyType type;
  String policyId;
  String resourceClass;
  String resourceId;
}
