# JFQLConnector

With the [JFQLConnector](https://joker-games.org/documentation/connector/download) you can connect to the MyJFQL DMBS.
There is support for JavaScript, Python and Java. You can, however, write a connector yourself in another language such
as C#. The language only needs JSON support and an HTTP client.

The example program listed here would log into a database which is located on port *2291* local on the server or computer. There it logs on with the user data *root* and *pw*. Then a table with the name *Users* is created there. All values are then read out from this and printed on the console.

### Java

```java

import org.jokergames.jfql.connection.Connection;
import org.jokergames.jfql.util.Column;
import org.jokergames.jfql.util.Result;
import org.jokergames.jfql.util.User;

import java.util.List;

public class JFQLProjectExample {


    public static void main(String[] args) {
        /**
         * If you want to connect your application to a MyJFQL database you have to create a connection with the Connection class.
         * You can enter your connection information like your host, username and password in the constructor of the Connection or in the connect method.
         * Below is an example for both cases:
         */

        final Connection connection = new Connection("myjfql:your-host.com", new User("your-user-name", "your-user-password"));
        connection.connect();

        /**
         * Or you enter the information in the connect method (this could be helpful so that to connection object can't produce a NullPointerException)
         */

        final Connection connection = new Connection();
        connection.connect("myjfql:your-host.com", new User("your-user-name", "your-user-password"));

        /**
         * What you also can do is to only enter the host or the user in the constructor and the connect method, like this:
         */

        final Connection connection = new Connection("myjfql:your-host.com");
        connection.connect(new User("your-user-name", "your-user-password"));


        /**
         * If a connection has been successfully established, commands can be sent to the database.
         * You can to this in two ways: you send a query and don't use the response, or you use the response.
         *
         * In this first case a query will be sent to the database. The JFQLConnector has some extras to simplify this for you.
         */

        connection.query("create table Example structure Column1 Column2 Column3");

        /**
         * To integrate variables in your query you can write '%' and enter the variable after.
         * If you do this the '%' will be replaced and the toString method of your entered object will be called also.
         * And the first entered argument after the query replaces the first entered '%' in the query.
         */

        connection.query("create table Example structure % % %", "Column1", "Column2", "Column3");

        /**
         * When something went wrong with your query an exception will be called. To impede this you can add an ', false' to your query.
         * This could be helpful if you want to create a database if it doesn't exist already, but everytime you query this and the database
         * already exist an exception will be called.
         */

        connection.query("create table Example structure Column1 Column2 Column3", false);

        /**
         * You can also use this with the replacement feature combined. But there it is important to add the false before the replacement variables.
         */

        connection.query("create table Example structure % % %", false, "Column1", "Column2", "Column3");

        /**
         * Maybe you want the result of your query. You can get it like this:
         */

        final Result result = connection.query("select value * from Example");

        result.getCode(); //returns the http (error) code
        result.getType(); //returns the type of response

        result.getResponse(); //returns the howl response in an JSONObject
        result.getEncryption(); //this returns the encryption of the result (this is a deprecated old unused feature)

        //those methods return the structure of the query (important if case of a table)
        result.getStructureArray();
        result.getStructureList();

        /**
         * The actual result for example the content of a table is accessible by the getColumns function. This function returns a list with all columns:
         */

        final List<Column> columns = result.getColumns();

        /**
         * To check if something is inside this columns list you can use the size function of the list:
         */

        if (columns.size() == 0) {
            //Nothing is in the columns list!
        } else {
            //Do something...
        }

        /**
         * For example, you want to get all rows of a table you can use:
         */

        final List<Column> columns = connection.query("select value * from Example").getColumns();

        /**
         * You can go through this with a for loop, for example.
         * Now you have the column and like the result class it has some features.
         */

        for (Column column : columns) {
            column.getEncryption(); //this returns the encryption of the result (this is a deprecated old unused feature)
            column.getJsonObject(); //returns the source of the column as JSONObject

            column.getCreation(); //returns the date when the column was created (is ms).

            /**
             * There are two types of results, with one there are theoretically several columns that could be present.
             * The column whose value you would like to have must be explicitly specified again (select query only). Like here:
             */

            column.getString("Column1");

            /**
             * In the second case, each column has and can only have one value.
             * There you do not have to specify the desired column (list, structure query only). Like this:
             */

            column.getString();

            /**
             * You can convert you result also in other data types:
             * - getLong
             * - getInteger
             * - getDouble
             * - getFloat
             * - getBoolean
             * - getShort
             */

        }
    }
}
```

```xml
<repositories>
    <repository>
        <id>joker-games</id>
        <url>https://joker-games.org/repository</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.joker-games</groupId>
        <artifactId>JFQLConnector</artifactId>
        <version>1.1</version>
    </dependency>
</dependencies>
```

### Python

```python
import connector

# Create connection
connection = connector.Connection("http://localhost:2291/query", connector.User("root", "pw"))

connection.query("CREATE TABLE Users STRUCTURE Name Password Email")

# Select values
result = connection.query("SELECT VALUE * FROM Users")
print(result)
```

### JavaScript

```javascript
//Create connection
var connection = new Connection('http://localhost:2291/query', 'root', 'pw')

connection.query('CREATE TABLE Users STRUCTURE Name Password Email')

//Select values
let response
connection.query('SELECT VALUE * FROM Users', (json) => response = json)
console.log(response)
```

### Documentation

To see detailed documentation on JFQLConnector [here](https://joker-games.org/documentation/connector/python/).


### License

All files on this repository are subject to the MIT license. Please read
the [LICENSE](https://github.com/joker-games/JFQLConnector/blob/master/LICENSE) file at the root of the project.

