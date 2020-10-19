# TransactionMonitor
Monitor of annotation Transactional in Spring Data

Transactions are managed declaratively in Spring Data.
For declarative management, the @Transactional annotation is used.
This annotation is placed above the method or class. The latter is equivalent to marking all methods with this annotation.
The class that uses @Transactional must be marked with the @Component or @Controller or @Service annotation, or similar.
This is necessary for Spring to form objects of this class as beans.
When creating such a bean, Spring creates it as a proxy, and takes the methods marked @Transactional in transactional brackets.
Transactional brackets mean that a new transaction will be created or an existing transaction will be continued before the method is executed, according to the propagation annotation parameter
For more information, see the org.springframework.transaction.annotation class.Propagation.
Keep in mind that Spring does not change the class, but uses its proxy!
Therefore, calling the bean_proxy.method1() will be implemented with transactional brackets, while calling this.method1() or the objectOfClass.method1() will still be without transactional brackets.
You can often find a mysterious rule in Internet: a method marked with the @Transactional annotation cannot be called from the class body (inside a method of the same class).
and you need to call it from methods of another class, otherwise the annotation will not work.
In fact, there is no mystery here. It's just that when you call a class method in the class body, you write method1(), which means this.method1(), and the transactional brackets don't work.
And when a method is called from another class, then in another class we get a label on the bean of the first class and write a call bean_proxy.method1().
If a class has a reference to a bean of the same class (for example, @Autowired classname bean_proxy), the method marked @Transactional can be called inside the class body as a bean_proxy.method1() and it will be executed in transactional brackets
There are many other rules for working with @Transactional:
1)the @Transactional annotation does not work on static methods
2)the @Transactional annotation does not work on default methods
3)the @Transactional annotation does not work on private and protected methods
4)distinguish the org.springframework.transaction.annotation.Transactional annotation from the javax.transaction.Transactional annotation.
In this case, we are talking about org.springframework.transaction.annotation.Transactional annotations.

However, there are many other factors that can make the situation with using transactions confusing.
For example, a method marked @Transactional starts execution of another method in a new thread.
In order to understand such situations, you need to use transaction diagnostics.
There are different approaches for performing transaction diagnostics.

A Transaction Monitor is offered here. It is executed as a java class TransactionMonitor.java.
To use it, you need to embed it in the app and make a call
TransactionMonitor.transactionSessionInfo("Description of the location for which the status of the transaction monitor is displayed");
in methods marked @Transactional and where the transaction situation is not transparent.
It is probably best to put it at the beginning of the method, but you can also put it in other places of the method.
The transaction monitor collects information and outputs it to the log using the private static void printInfo(String msg) method.
The information is as follows:
1)implementation of TransactionManager. This version of the TransactionMonitor only works for the implementation of org.springframework.orm.jpa.JpaTransactionManager!
2)the value of the hibernate.current_session_context_class parameter
3)the value of the jdbc.autoCommit parameter. If this parameter is set to true, jdbc wraps each insert/update/delete statement in transactional brackets, and in this case, the application does not actually manage transactions.
In particular, this means that @Transactional does not work or is ignored due to incorrect usage, since the implementation of this annotation requires setting jdbc.autoCommit=false.
4)transaction name - usually it corresponds to the name of the method in which it started, and if it is null, the transaction probably did not start.
5)transaction resources - primarily session - EntityManager(s) (or Session(s)) covered by the transaction.
6)is the session marked as rollBackOnly
7)other parameters

So, just include the TransactionMonitor.java in your application and add TransactionMonitor.transactionSessionInfo( label ) call in the methods marked @Transactional.
