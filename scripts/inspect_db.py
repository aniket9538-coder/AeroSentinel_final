import psycopg2

conn = psycopg2.connect(host='localhost', port=5432, dbname='aerosentinel', user='aerosentinel_user', password='change_this_in_production')
cur = conn.cursor()
cur.execute("""
    SELECT tc.table_name, kcu.column_name 
    FROM information_schema.table_constraints AS tc 
    JOIN information_schema.constraint_column_usage AS ccu ON ccu.constraint_name = tc.constraint_name 
    JOIN information_schema.key_column_usage AS kcu ON tc.constraint_name = kcu.constraint_name 
    WHERE tc.constraint_type = 'FOREIGN KEY' AND ccu.table_name = 'federated_nodes'
""")
print('Referencing tables:', cur.fetchall())
conn.close()
