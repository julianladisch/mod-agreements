package com.k_int.accesscontrol.core.policycontrolled;

import java.lang.annotation.*;

/**
 * Annotation to mark classes that are controlled by access policies.
 * <p>
 * This annotation is used to specify the resource and owner identifiers for policy evaluation.
 * It allows the policy engine to determine the resource and ownership hierarchy for access control.
 * </p>
 * <p>
 * At runtime, classes such as {@code PolicyControlledManager} use this annotation to
 * introspect resource and ownership metadata. This enables generic policy enforcement
 * by dynamically extracting resource identifiers and ownership relationships from annotated classes.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PolicyControlled {
  /**
   * The name of the database column that identifies the resource.
   * <p>
   * This is used to determine the resource for policy evaluation.
   * </p>
   * @return the resource column name
   */
  String resourceIdColumn() default "";
  /**
   * The name of the field in the class that holds the resource's unique identifier.
   * <p>
   * This is used to determine the resource for policy evaluation.
   * </p>
   * @return the resource ID field name
   */
  String resourceIdField() default "";


  // Allow us to roam up an ownership tree
  /**
   * The name of the database column that identifies the owner of the resource.
   * <p>
   * This is used to determine the ownership hierarchy for policy evaluation.
   * </p>
   * @return the owner column name
   */
  String ownerColumn() default "";
  /**
   * The name of the field in the class that holds the owner's unique identifier.
   * <p>
   * This is used to determine the ownership hierarchy for policy evaluation.
   * </p>
   * @return the owner field name
   */
  String ownerField() default "";
  /**
   * The class that owns the resource, used to determine the ownership hierarchy.
   * <p>
   * Defaults to {@code Object.class} if not specified, indicating no specific owner.
   * </p>
   * @return the owner class
   */
  Class<?> ownerClass() default Object.class;
}
