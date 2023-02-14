# -*- coding: utf-8 -*-
#!/usr/bin/python
import MySQLdb

def get_obj(host, db="histo_quiz"):
    '''
    The name of database of MySQL is inputted as the parameter db
    '''
    ms = MySQL()
    ms.set_connection(db, host)
    return ms

class MySQL:
    def __init__(self):
        self.user = 'test'   ## Input the username of MySQL
        self.passwd = 'test' ## Input the password of the username 

    def set_connection(self, dbname, hst):
        self.con = MySQLdb.connect(
            host=hst,
            db=dbname,
            user=self.user,
            passwd=self.passwd,
            charset="utf8mb4")
            #charset="utf8")
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

    def get_data(self, dbname, tname, select, where):
        cur = MySQLdb.connect(
            host='localhost',
            db=dbname,
            user=self.user,
            passwd=self.passwd,
            #charset="utf8mb4").cursor()
            charset="utf8").cursor()

        com = 'SELECT ' + select + ' FROM ' + tname
        if len(where) > 0:
            com += ' WHERE ' + where

        cur.execute(com)

        res = cur.fetchall()
        cur.close()
        return res

    def exec_sql(self, dbname, tname, select, opt):
        cur = MySQLdb.connect(
            host='localhost',
            db=dbname,
            user=self.user,
            passwd=self.passwd,
            charset="utf8").cursor()

        if len(opt) > 0:
            com = 'SELECT ' + select + ' FROM ' + tname + ' ' + opt
        else:
            com = 'SELECT ' + select + ' FROM ' + tname

        cur.execute(com)

        res = cur.fetchall()
        cur.close()
        return res
