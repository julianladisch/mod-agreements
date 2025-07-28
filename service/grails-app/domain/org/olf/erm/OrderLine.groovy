package org.olf.erm

import com.k_int.web.toolkit.domain.traits.Clonable

import grails.gorm.MultiTenant
import groovy.util.logging.Slf4j

public class OrderLine implements MultiTenant<OrderLine>, Clonable<OrderLine> {
	
	String id
	String poLineId
	
	static belongsTo = [ owner: Entitlement ]
  
	  static mapping = {
  //    table 'order_lines'
					 id column: 'pol_id', generator: 'uuid2', length:36
				version column: 'pol_version'
				  owner column: 'pol_owner_fk'  
			   poLineId column: 'pol_orders_fk'
	}
  
	static constraints = {
		 owner(nullable:false, blank:false);
	   poLineId(nullable:true, blank:false);
	}
  
  /**
   * Need to resolve the conflict manually and add the call to the clonable method here.
   */
  @Override
  public OrderLine clone () {
    Clonable.super.clone()
  }
}
