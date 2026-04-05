box.cfg{
    listen = 3301
}

box.schema.user.grant('guest', 'read,write,execute', 'universe', nil, {if_not_exists = true})

local space = box.schema.space.create('KV', {if_not_exists = true})
space:format({
    {name = 'key', type = 'string'},
    {name = 'value', type = 'varbinary', is_nullable = true}
})
space:create_index('primary', {
    if_not_exists = true,
    parts = {
        {field = 'key', type = 'string'}
    }
})
