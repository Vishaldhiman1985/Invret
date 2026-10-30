package com.invret.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Amounts are stored as integer paise. Dates use ISO YYYY-MM-DD; months YYYY-MM. */
class LedgerDb(ctx: Context): SQLiteOpenHelper(ctx, "invret.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE projects(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE investors(id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL,name TEXT NOT NULL,FOREIGN KEY(project_id) REFERENCES projects(id))")
        db.execSQL("CREATE TABLE contributions(id INTEGER PRIMARY KEY AUTOINCREMENT,investor_id INTEGER NOT NULL,day TEXT NOT NULL,amount INTEGER NOT NULL CHECK(amount>0),mode TEXT NOT NULL,notes TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE agreements(id INTEGER PRIMARY KEY AUTOINCREMENT,investor_id INTEGER NOT NULL,start_month TEXT NOT NULL,end_month TEXT,type TEXT NOT NULL CHECK(type IN ('FIXED','REVENUE','PROFIT','HYBRID')),fixed_amount INTEGER NOT NULL DEFAULT 0,rate REAL NOT NULL DEFAULT 0,notes TEXT NOT NULL DEFAULT '',CHECK(end_month IS NULL OR end_month>=start_month))")
        db.execSQL("CREATE TABLE financials(id INTEGER PRIMARY KEY AUTOINCREMENT,project_id INTEGER NOT NULL,month TEXT NOT NULL,revenue INTEGER NOT NULL DEFAULT 0,expenses INTEGER NOT NULL DEFAULT 0,notes TEXT NOT NULL DEFAULT '',UNIQUE(project_id,month))")
        db.execSQL("CREATE TABLE payments(id INTEGER PRIMARY KEY AUTOINCREMENT,investor_id INTEGER NOT NULL,day TEXT NOT NULL,amount INTEGER NOT NULL CHECK(amount>0),mode TEXT NOT NULL,notes TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE locks(project_id INTEGER NOT NULL,month TEXT NOT NULL,PRIMARY KEY(project_id,month))")
        db.execSQL("CREATE TABLE adjustments(id INTEGER PRIMARY KEY AUTOINCREMENT,investor_id INTEGER NOT NULL,month TEXT NOT NULL,amount INTEGER NOT NULL,notes TEXT NOT NULL)")
        db.execSQL("CREATE INDEX idx_agreements ON agreements(investor_id,start_month)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion:Int, newVersion:Int) { error("Explicit migration required; data must not be dropped") }
    fun rows(sql:String, args:Array<String> = emptyArray()):List<Map<String,String>> {
        val result= mutableListOf<Map<String,String>>()
        readableDatabase.rawQuery(sql,args).use { c -> while(c.moveToNext()) { val m=mutableMapOf<String,String>(); for(i in 0 until c.columnCount) m[c.getColumnName(i)] = c.getString(i) ?: ""; result.add(m) } }
        return result
    }
    fun scalar(sql:String,args:Array<String> = emptyArray()):Long = rows(sql,args).firstOrNull()?.values?.firstOrNull()?.toLongOrNull() ?: 0L
    fun locked(project:Long,month:String)=scalar("SELECT COUNT(*) FROM locks WHERE project_id=? AND month=?",arrayOf(project.toString(),month))>0
    fun earned(investor:Long,month:String):Long {
        val agreement=rows("SELECT * FROM agreements WHERE investor_id=? AND start_month<=? AND (end_month IS NULL OR end_month>=?) ORDER BY start_month DESC,id DESC LIMIT 1",arrayOf(investor.toString(),month,month)).firstOrNull() ?: return 0
        val project=scalar("SELECT project_id FROM investors WHERE id=?",arrayOf(investor.toString()))
        val financial=rows("SELECT revenue,expenses FROM financials WHERE project_id=? AND month=?",arrayOf(project.toString(),month)).firstOrNull()
        val revenue=financial?.get("revenue")?.toLongOrNull() ?: 0L
        val profit=(revenue-(financial?.get("expenses")?.toLongOrNull() ?: 0L)).coerceAtLeast(0)
        val fixed=agreement["fixed_amount"]!!.toLong()
        val rate=agreement["rate"]!!.toDouble()
        val variable=when(agreement["type"]) { "REVENUE" -> revenue; "PROFIT" -> profit; "HYBRID" -> profit; else -> 0L }
        val base=when(agreement["type"]) { "FIXED" -> fixed; "HYBRID" -> fixed; else -> 0L }
        val calculated=base+Math.round(variable*rate/100.0)
        return calculated+scalar("SELECT COALESCE(SUM(amount),0) FROM adjustments WHERE investor_id=? AND month=?",arrayOf(investor.toString(),month))
    }
}
