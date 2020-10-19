package tm;

/*
  Copyright 2021 Mikhail Konopko 

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

/**
 * Usage: TransactionMonitor.transactionSessionInfo("Description of the location for which the status of the transaction monitor is displayed");
 * 
 * @author Mikhail Konopko 
 * @version 1.0 
 *
 */

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.internal.SessionImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class TransactionMonitor {

		public static TransactionMonitor transactionMonitor;

		@Autowired
		ApplicationContext applicationContext;

		private boolean breakeMonitoring = false;
		
		private static void printInfo(String msg) {
				logger.info( msg );
		}
		static Logger logger = LoggerFactory.getLogger(TransactionMonitor.class);

		public TransactionMonitor() {
			transactionMonitor = this;
		}
		
		public static void transactionInfo(String comment) {
			transactionInfoLnternal(comment, true);
		}

		public static void transactionInfo(String comment, boolean lastLine) {
			transactionInfoLnternal(comment, true);
		}

		public static Long transactionInfoLnternal(String comment, boolean lastLine) {
			Long timeBeg = System.currentTimeMillis(); 
														
			printInfo("\n|---------------------------------------------------------------------------------------------------------------");
			printInfo("|-- TRANSACTION MONITOR -- <<" + comment + ">> ------ thread=" + Thread.currentThread().getName() + "-- begin -- ");
			
			sayAboutTransactionalConfig();
			if (!transactionMonitor.breakeMonitoring) sayAboutTransactionState();
			if (!transactionMonitor.breakeMonitoring) sayAboutTransactionResources();
			
			if (lastLine) printInfo("|-- TRANSACTION MONITOR -- <<" + comment + ">> ----------------------- end ----"
						+ (System.currentTimeMillis() - timeBeg) + " millisec-- ");
			return timeBeg;
		}
		
		
	private static void sayAboutTransactionState() {
			try {
				    printInfo("|-------------- transaction state -----------");
						
					Map<EntityManager, Boolean> mapEntityManagerToAutoCommit = new HashMap<>();
					Map<EntityManager, Boolean> mapEntityManagerToRollback = new HashMap<>();
					Map<EntityManager, Boolean> mapEntityManagerToIsActive = new HashMap<>();
					Map<EntityManager, String>  mapEntityManagerToStatus = new HashMap<>(); 
					getEntityManagerHolders().stream()
						.forEach( entityManagerHolder -> {
							EntityManager entityManager = null;
							if (entityManagerHolder != null) {
								entityManager = entityManagerHolder.getEntityManager();

								if (entityManager != null) {
									//------ AutoCommit ----------------
									if (entityManager.unwrap(Session.class) instanceof SessionImpl)
										try { mapEntityManagerToAutoCommit
											      .put(entityManager, ((SessionImpl) entityManager.unwrap(Session.class)).connection().getAutoCommit());
										} catch (HibernateException | SQLException e) { e.printStackTrace();	}
									else mapEntityManagerToAutoCommit.put(entityManager, null);

									//------ state --------------------
									try {
									   mapEntityManagerToStatus.put( entityManager, entityManager.unwrap(Session.class).getTransaction().getStatus().name());
									} catch (Exception e) { mapEntityManagerToStatus.put( entityManager, "the transaction state is undefined!!!"); } 

									//------ RollbackOnly --------------------
									try {
										mapEntityManagerToRollback.put( entityManager, entityManager.unwrap(Session.class).getTransaction().getRollbackOnly());
									} catch (Exception e) { mapEntityManagerToRollback.put( entityManager, null); } 

									//------ isActive --------------------
									try {
										mapEntityManagerToIsActive.put( entityManager, entityManager.unwrap(Session.class).getTransaction().isActive());
									} catch (Exception e) { mapEntityManagerToIsActive.put( entityManager, null); } 
								}
								
								
						}
					}
					);
					//------ AutoCommit ----------------
					if ( mapEntityManagerToAutoCommit.isEmpty() ) printInfo("|The jdbc.autoCommit parameter cannot be determined" );
					else {
						Map<Boolean, List<Entry<EntityManager, Boolean>>> mapAutoCommitToEntityManager = mapEntityManagerToAutoCommit.entrySet().stream()
							.collect(Collectors.groupingBy(e->e.getValue()));
						if ( mapAutoCommitToEntityManager.size() == 1 ) 
							mapAutoCommitToEntityManager.entrySet().stream().forEach( e-> {
								if (e.getKey()) printInfo("|Transactions are not managed!!! commit/rollback occurs at the level of a separate sql operator. jdbc.autoCommit=true" );
								else printInfo("|Transactions are managed!!! jdbc.autoCommit=false" );  
							});
						else {
							printInfo("|Contradiction in transaction management, part of EntityManager is enabled, part of it is disabled");   
							mapEntityManagerToAutoCommit.entrySet().stream().forEach( e -> 
									printInfo("| "+e.getKey()+" has parameter jdbc.autoCommit="+e.getValue()));
							printInfo("|\n");
						}
					}

					printInfo("|transaction name (usually the name of the method where the transaction started)=" + TransactionSynchronizationManager.getCurrentTransactionName());

					//------ state --------------------
					if ( mapEntityManagerToStatus.isEmpty() ) printInfo("| transaction state cannot be determined" );
					else {
						Map<String, List<Entry<EntityManager, String>>> mapStatusToEntityManager = mapEntityManagerToStatus.entrySet().stream()
							.collect(Collectors.groupingBy(e->e.getValue()));
						if ( mapStatusToEntityManager.size() == 1 ) 
							mapStatusToEntityManager.entrySet().stream().forEach( e-> { printInfo("|transaction state="+e.getKey() ); });
						else {
							printInfo("|EntityManagers of transactions that participate in resources have different transaction states;");   
							mapEntityManagerToStatus.entrySet().stream().forEach( e -> 
									printInfo("|У "+e.getKey()+" transaction state="+e.getValue()));
							printInfo("|\n");
						}
					}
					
					//------ RollbackOnly --------------------
					if ( mapEntityManagerToRollback.isEmpty() ) printInfo("|RollbackOnly state of transaction cannot be determined" );
					else {
						Map<Boolean, List<Entry<EntityManager, Boolean>>> mapRollBackToEntityManager = mapEntityManagerToRollback.entrySet().stream()
							.collect(Collectors.groupingBy(e->e.getValue()));
						if ( mapRollBackToEntityManager.size() == 1 ) 
							mapRollBackToEntityManager.entrySet().stream().forEach( e->  
								printInfo("|transaction " + (e.getKey() ? "RollbackOnly" : "not marked as RollbackOnly" )));
						else {
							printInfo("|EntityManager transactions participating in resources have different RollbackOnly transaction values:");   
							mapEntityManagerToRollback.entrySet().stream().forEach( e -> 
									printInfo("|"+e.getKey()+" RollbackOnly="+e.getValue()));
									printInfo("|\n");
						}
					}
					
					//------ isActive --------------------
					if ( mapEntityManagerToIsActive.isEmpty() ) printInfo("|isActive state of transaction cannot be determined" );
					else {
						Map<Boolean, List<Entry<EntityManager, Boolean>>> mapIsActiveToEntityManager = mapEntityManagerToIsActive.entrySet().stream()
							.collect(Collectors.groupingBy(e->e.getValue()));
						if ( mapIsActiveToEntityManager.size() == 1 ) 
							mapIsActiveToEntityManager.entrySet().stream().forEach( e->  
								printInfo("|transaction " + (e.getKey() ? "active" : "not active" )));
						else {
							printInfo("|EntityManager transactions that participate in resources have different isActive transaction values:");   
							mapEntityManagerToIsActive.entrySet().stream().forEach( e -> 
									printInfo("|"+e.getKey()+" isActive="+e.getValue()));
									printInfo("|\n");
						}
					}

					

				printInfo("|");
				printInfo("|         ---  another transaction parameters  ---:");
				TransactionStatus status = TransactionAspectSupport.currentTransactionStatus();
				String transactionParametrs = "|" + 
					((TransactionSynchronizationManager.isActualTransactionActive()) ? "active" : "not active" ) + "; " +
					"isolation level " + isolationName(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel()) + "; " +			
					(TransactionSynchronizationManager.isCurrentTransactionReadOnly() ? "readOnly" : "readWrite") + "; " +
					(status.isNewTransaction() ? "new" : "not new") + "; " +
					(status.hasSavepoint() ? "has savePoint" : "doesn't have savePoint") + "; " +
					(status.isCompleted() ? " completed" : " not completed") + "; " +
					(status.isRollbackOnly() ? "RollbackOnly" : "not RollbackOnly");
				printInfo(transactionParametrs);
					
				printInfo("|"); 
			} catch( Exception e ) { printInfo( "sayAboutTransactionState()="+e.getMessage() ); }; 
			
		}
	private static void sayAboutTransactionResources() {
			printInfo("|");
			printInfo("|-------------- resources of the current transaction (key=value) ---------------:");
			Map<Object, Object> mapResources = TransactionSynchronizationManager.getResourceMap();
			if (mapResources == null)
				printInfo("| the transaction has no resources!");
			else
				mapResources.entrySet().stream().forEach(e -> printInfo("|     * " + e.getKey() + "=" + e.getValue()));

			try {
				printInfo("|            --- synchronization elements of transaction --- " );
				printInfo("|" + (TransactionSynchronizationManager.isSynchronizationActive()
						? "Transaction synchronization is activated:"
						: ":"));
				List<TransactionSynchronization> listTransactionSynchronization = null;
				if (TransactionSynchronizationManager.isSynchronizationActive()) {
					listTransactionSynchronization = TransactionSynchronizationManager.getSynchronizations();
					if (listTransactionSynchronization.isEmpty())
						printInfo("|    The transaction currently has no syncing elements!");
					else
						listTransactionSynchronization.stream().forEach(s -> printInfo("|    " + s.toString()));
				} else
					printInfo("|   Transaction synchronization is not activated (there are no synchronization elements)!");
			} catch (Exception e) {
				printInfo(e.getMessage()); 
			}
			
		}
	private static void sayAboutTransactionalConfig() {
		try {
			    printInfo("|-------------- elements of the configuration -----------");
			    String transactionManagerImplementation = transactionMonitor.applicationContext
	                     										.getBean("transactionManager").getClass().getCanonicalName(); 
				printInfo("|implementation of transactionManager=" + transactionManagerImplementation );
				if ( !transactionManagerImplementation.equals("org.springframework.orm.jpa.JpaTransactionManager")) { 
					printInfo("|This version of the transaction monitor only works for implementation of transactionManager=org.springframework.orm.jpa.JpaTransactionManager!!!" );
					transactionMonitor.breakeMonitoring = true;
					return;
				}	
				
				Map<EntityManager, String> mapEntityManagerToClassName = new HashMap<>();
				getEntityManagerHolders().stream()
					.forEach( entityManagerHolder -> {
						EntityManager entityManager = null;
						if (entityManagerHolder != null) {
							entityManager = entityManagerHolder.getEntityManager();

							if (entityManager != null) {
								Session session = entityManager.unwrap(Session.class);
								String current_session_context_class = session.getEntityManagerFactory().getProperties().entrySet().stream()
								.filter(pr -> pr.getKey().equals("hibernate.current_session_context_class"))
								.map( e->e.getKey()+"="+e.getValue())
								.findAny().orElse( "hibernate.current_session_context_class is not set!!!" );
							mapEntityManagerToClassName.put(entityManager, current_session_context_class);
							}
					}
				}
				);
				if ( mapEntityManagerToClassName.isEmpty() ) printInfo("|The hibernate.current_session_context_class parameter cannot be determined because the transaction is not associated with any EntityManager" );
				else {
					mapEntityManagerToClassName.entrySet().stream().forEach( e -> 
					    								printInfo("|"+e.getKey()+" parameter "+e.getValue()));  
				}
				printInfo("|"); 

		} catch( Exception e ) { printInfo( "sayAboutTransactionalConfig()="+e.getMessage() ); }; 
			
	}
		
	public static List<EntityManagerHolder> getEntityManagerHolders() {
		try {
			Map<Object, Object> mapResources = TransactionSynchronizationManager.getResourceMap();
			List<EntityManagerHolder> sessionList = new ArrayList<>();
			if (mapResources != null)
				mapResources.entrySet().stream().filter(e -> e.getValue() instanceof EntityManagerHolder)
						.forEach(e -> sessionList.add((EntityManagerHolder) e.getValue()));
			return sessionList;
		} catch (Exception e) {		printInfo(e.getMessage());		return null;	}
	}
		




		/**
		 * Return the isolation level for the current transaction, if any. To be called
		 * by resource management code when preparing a newly created resource (for
		 * example, a JDBC Connection).
		 * 
		 * @return the currently exposed isolation level, according to the JDBC
		 *         Connection constants (equivalent to the corresponding Spring
		 *         TransactionDefinition constants), or {@code null} if none
		 * @see java.sql.Connection#TRANSACTION_READ_UNCOMMITTED
		 * @see java.sql.Connection#TRANSACTION_READ_COMMITTED
		 * @see java.sql.Connection#TRANSACTION_REPEATABLE_READ
		 * @see java.sql.Connection#TRANSACTION_SERIALIZABLE
		 * @see org.springframework.transaction.TransactionDefinition#ISOLATION_READ_UNCOMMITTED
		 * @see org.springframework.transaction.TransactionDefinition#ISOLATION_READ_COMMITTED
		 * @see org.springframework.transaction.TransactionDefinition#ISOLATION_REPEATABLE_READ
		 * @see org.springframework.transaction.TransactionDefinition#ISOLATION_SERIALIZABLE
		 * @see org.springframework.transaction.TransactionDefinition#getIsolationLevel()
		 */


		public static String isolationName(Integer isolationLevel) {
			if (isolationLevel == null)
				return "not defined";
			if (isolationLevel == -1)
				return "ISOLATION_DEFAULT (-1)";
			if (isolationLevel == 1)
				return "ISOLATION_READ_UNCOMMITTED (1)";
			if (isolationLevel == 2)
				return "ISOLATION_READ_COMMITTED (2)";
			if (isolationLevel == 4)
				return "ISOLATION_REPEATABLE_READ (4)";
			if (isolationLevel == 8)
				return "ISOLATION_SERIALIZABLE (8)";
			return "new (" + isolationLevel + ")";
		}




	}
