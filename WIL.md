# What I've learned

## Using HugSQL

### `-- :name` to define query name/type

- `-- :name save-messages! :! :n`
  - Creates query called `save-messages!`, and it ends with `!` since it mutates the data.
  - `:!` means that query is destructive.
  - `:n` means that query returns the number of affected rows.
- `-- :name get-messages :? :*`
  - `:?` means that query does a select.
  - `:*` means that query returns multiple rows.
