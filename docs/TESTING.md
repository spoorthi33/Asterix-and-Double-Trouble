## Testing Strategy

We have implemented testing in two phases. For order and catalog service's we have implemented unit test's to verify functionality.
To test overall flow, we have separated test-cases

## Unit Testing

### Catalog Service

In `src/src/catalog/src/test/java/CatalogTests.java` unit test's have been implemented to test query and buy flow.

Below image show's that all the test cases have been passed.
![plot](resources/CatalogUnitTests.png)

### Order Service

In `src/src/order/src/test/java/OrderTests.java` unit test's have been implemented to test placing order flow.

Below image show's that all the test cases have been passed.
![plot](resources/OrderUnitTests.png)

Below image show's that order.csv file has been updated successfully
![plot](resources/OrderUTResult.png)

## Flow Testing

### Caching: 

#### Cache working :

Lets make a query request to front-end for  `Whale` twice, initially it should make call to catalog, 2nd time it should fetch from cache.

![plot](resources/CacheTest1.png)

We can also see catalog service is only queried once

![plot](resources/CacheTest1Catalog.png)

#### LRU Cache:

We will verify if our cache is honoring LRU, let's call `Lego` 1st and then other 10 items , now Lego should be evicted from cache.

![plot](resources/CacheEviction.png)

In above image, Lego is evicted when cache size is reached.

#### Cache invalidation on Buy:

We will 1st query `Lego` twice, then we will buy `Lego` and then query again.

![plot](resources/CacheInvalidationBuy.png)

As you can see, we fetched from cache 2nd time and later on cache was invalidated on buy request, hence we again queried catalog service.

#### Cache invalidation on Restock:

Let's call `Tux` 110 times , 1st 100 calls will succeed , after that restocking will take place and invalidate cache will be called.

![plot](resources/CatalogRestock.png)

Catalog has restocked items and then calls front-end for invalidation

![plot](resources/FrontEndRestock.png)

Front-End has received bulk request(could be multiple items restocked).

### Replication:

#### Leader Election:

Let's start 3 replicas of order service with id's 1,2,3.

Let's start front-end service with below config

![plot](resources/FrontEndConfig.png)

![plot](resources/LeaderElectionFrontEnd.png)

We can see that node 3 has been elected as leader

![plot](resources/LeaderElectionOrder.png)

We can see above, leader node has been notified about leader election and follower nodes

#### Order Propagation:

Let's do a buy operation on clean slate for all the items.

![plot](resources/OPLeader.png)

We can see that leader Node(3) is propagating order to follower nodes(1,2)

![plot](resources/OPFollower.png)

We can see that follower node has been received by follower

![plot](resources/OPOrderFile.png)

We can see initially all the files are empty, later we can see all the files have been updated with order details.

### Fault Tolerance:

#### Crash Detection (Follower Node):

With same above environment, let's first crash follower node 1 and see 

![plot](resources/FTFrontEnd.png)

We can see,Front-End identified node 1 has crashed and informed leader about crash.

Let's make 5 buy requests and see that updates are made only to node 2

![plot](resources/FTOrderUpdate1.png)

#### Sync when follower node comes online:

Let's look at state of order files before node1 comes online

![plot](resources/FTOrderComp1.png)

Now lets bring node 1 online

![plot](resources/FTFollowerJoin1.png)

We can see, follower node is trying join cluster, getting data from leader node and sync data.

![plot](resources/FTFrontEndJoin1.png)

We can see, request has been made to join cluster, updates leader and once leader accepts, new node has joined the cluster

We lets compare 2 files and check state of DB

![plot](resources/FTOrderComp2.png)

We can see both the files are synchronized.

#### Crash Detection and Sync in case of leader:

Now let's crash leader node and check 

![plot](resources/FTFELeader.png)

We can see that front-end is reelecting since crashed node is leader

![plot](resources/FTNewLeader.png)

In replica 2 logs, we can see replica3 was leader before,now it has been elected as the leader.

Let's make 5 calls to front-end service and compare DB files associated with replica 2(current leader) and replica 3(prev leader)

![plot](resources/FTOrderComp1Leader.png)

We can see that DB of replica2 is updated but not of replica3.

Let's bring back crashed node and check sync.

![plot](resources/FTOrderComp2Leader.png)

We can see, replica 3 is now in sync with replica 2

### Consensus using RAFT:

Let's now run order replicas with useRaft flag as true.

#### Propagation of logs:

When leader node receives order, it will append log with transaction status as `P(pending)`, and replicates the log to follower node.

![plot](resources/leaderraftvoting.png)

Since all replicas are up we have received 3 positive votes. Since it got majority of the votes

1. It will commit the log and order is written into DB.
2. It will send ack to follower nodes

![plot](resources/raftleaderAck.png)

We can see that follower's received ack and they committed the log and also updates the transaction status to `S(success)`

#### Crash of 1 replica :

Let's crash replica 1, and see 

![plot](resources/raftcrash1.png)

We can see that log is still getting committed since it has majority of the votes.

#### Sync when crashed replica comes online:

Let's see state of log files and DB of replica 1 and replica 3(leader node) before we bring replica up..

![plot](resources/raftState1.png)

Let's bring crashed replica up...

![plot](resources/raftSync.png)

We can see that crashed replica will fetch lost data and synchronizes. Now see state of log files and DB of replica 1 and replica 3(leader node)

![plot](resources/raftState2.png)

We can see lost data is synchronized.

#### Crash of 2 replicas :

Let's crash 2 replicas (1,2)  

![plot](resources/raftFailure.png)

We can see that log has been rolled back since it didn't get majority voting

![plot](resources/raftClientError.png)

We can see that buy request has failed because of DB replication failure.

### Overall Response:

Overall response seen by client will same as lab2

![plot](resources/overallresp.png)





















































