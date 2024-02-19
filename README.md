# todo-list
Todo list rest api implemented using in memory data store 

```
$ clj
$ (require 'main)
$ (main/start-dev)
```

### Endpoints supported
```
POST /todo : create a new empty item list
GET /todo  : get all list items
GET /todo/:list-id : get items from a list
POST /todo/:list-id : save item inside a list
GET /todo/:list-id/:item-id : get item from the list
PUT /todo/:list-id/:item-id : update the item inside the list
DELETE /todo/:list-id/:item-id : delete item from the list
```

- Moving it to Ring http.
- Integrating with SQL.
- Routes Coercions means validation of Param.
- Test files.
