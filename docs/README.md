
## Steps to run:

Locate the binaries at resources folder in src/

#### Front-End Service

Update config file if required and then run

```
java -jar frontend-1.0-SNAPSHOT-jar-with-dependencies.jar config.properties
```

#### Catalog Service

```
java -jar catalog-1.0-SNAPSHOT-jar-with-dependencies.jar 9999 productCatalog.csv http://localhost:8889 false
```

1st arg - port
2nd arg - productCatalog file path
3rd arg - frontEndService url
4th arg - true(if cache is enabled)/false

#### Order Service

```
java -jar order-1.0-SNAPSHOT-jar-with-dependencies.jar 11111 1 order1.csv http://localhost:9999 http://localhost:8889 http://localhost:11111 False orderLog1.csv
```

1st arg - replica port
2nd arg - replica id
3rd arg - replica order db file path
4th arg - catalogService url
5th arg - frontEndService url
6th arg - replica's url
7th arg - True(if using raft)/False
8th arg - replica log file path

#### Client Service

```
java -jar client-1.0-SNAPSHOT-jar-with-dependencies.jar http://localhost:8889
```

1st arg - frontEndService url
