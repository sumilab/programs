# -*- coding: utf-8 -*-
#!/usr/bin/python
import MySQLdb

def get_obj(host, db="twitter"):
    ms = MySQL()
    ms.set_connection(db, host)
    return ms

class MySQL:
    def __init__(self):
        self.user = ''# Set MySQL user name
        self.passwd = ''# Set MySQL password

    def set_connection(self, dbname, hst):
        self.con = MySQLdb.connect(
            host=hst,
            db=dbname,
            user=self.user,
            passwd=self.passwd,
            charset="utf8mb4")
        self.cur = self.con.cursor()
        self.db = dbname

    def end_connection(self):
        self.cur.close()
        self.con.close()

    def insert(self, sql):
        self.cur.execute(sql)
        self.con.commit()

    def update(self, sql):
        self.cur.execute(sql)
        self.cur.fetchall()
        self.con.commit()

    def select(self, sql):
        try:
            self.cur.execute(sql)
        except:
            import time
            time.sleep(10)
            self.cur.execute(sql)
        return self.cur.fetchall()