package com.invret.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class MainActivity: Activity() {
    private lateinit var db:LedgerDb
    private lateinit var root:LinearLayout
    private var project=0L
    private var investor=0L
    private val csvRequest=101
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState);db=LedgerDb(this);home() }
    private fun money(paise:Long)= "₹"+String.format(Locale("en","IN"),"%,.2f",paise/100.0)
    private fun paise(s:String):Long { val d=s.trim().toBigDecimal();require(d.signum()>=0 && d.scale()<=2){"Enter a valid positive amount with up to 2 decimals"};return d.movePointRight(2).longValueExact() }
    private fun page(title:String) { val scroll=ScrollView(this);root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24) };scroll.addView(root);setContentView(scroll);text(title,24) }
    private fun text(s:String,size:Int=16) { root.addView(TextView(this).apply { text=s;textSize=size.toFloat();setPadding(0,9,0,9) }) }
    private fun button(label:String,action:()->Unit) { root.addView(Button(this).apply { text=label;setOnClickListener{action()} }) }
    private fun form(title:String,fields:List<Pair<String,String>>,done:(List<String>)->Unit) {
        val inputs=fields.map { (hint,default)-> EditText(this).apply { setSingleLine(true);setText(default);this.hint=hint } }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(20,0,20,0);inputs.forEach{addView(it)} }
        AlertDialog.Builder(this).setTitle(title).setView(box).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_->try {done(inputs.map{it.text.toString().trim()})}catch(e:Exception){Toast.makeText(this,e.message ?: "Invalid input",Toast.LENGTH_LONG).show()} }.show()
    }
    private fun insert(table:String,values:ContentValues) { db.writableDatabase.insertOrThrow(table,null,values) }
    private fun cv(vararg fields:Pair<String,Any?>)=ContentValues().apply { fields.forEach{(k,v)->when(v){is Long->put(k,v);is Int->put(k,v);is Double->put(k,v);null->putNull(k);else->put(k,v.toString())}} }
    private fun validDate(s:String)=LocalDate.parse(s).toString()
    private fun validMonth(s:String)=YearMonth.parse(s).toString()
    private fun home() {page("Invret · Projects");button("+ Add project") {form("New project",listOf("Project name" to "")){v->require(v[0].isNotBlank());insert("projects",cv("name" to v[0]));home()} }
        db.rows("SELECT * FROM projects ORDER BY id DESC").forEach{p->button(p["name"]!!){project=p["id"]!!.toLong();projectPage()} }
        text("Offline only · All amounts in INR · Back up your phone data regularly",13)
    }
    private fun projectPage() {val p=db.rows("SELECT name FROM projects WHERE id=?",arrayOf(project.toString())).firstOrNull() ?: return home();page(p["name"]!!);button("← Projects"){home()}
        button("+ Add investor") {form("New investor",listOf("Name" to "")){v->require(v[0].isNotBlank());insert("investors",cv("project_id" to project,"name" to v[0]));projectPage()} }
        button("+ Monthly revenue / expenses") {form("Project financials",listOf("Month YYYY-MM" to YearMonth.now().toString(),"Revenue ₹" to "0","Expenses ₹" to "0","Notes" to "")){v->val m=validMonth(v[0]);require(!db.locked(project,m)){"Month finalized; no silent edits"};val values=cv("project_id" to project,"month" to m,"revenue" to paise(v[1]),"expenses" to paise(v[2]),"notes" to v[3]);db.writableDatabase.insertWithOnConflict("financials",null,values,android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);projectPage()} }
        button("Finalize month") {form("Lock monthly calculations",listOf("Month YYYY-MM" to YearMonth.now().minusMonths(1).toString())){v->val m=validMonth(v[0]);insert("locks",cv("project_id" to project,"month" to m));projectPage()} }
        text("Monthly performance",20)
        db.rows("SELECT * FROM financials WHERE project_id=? ORDER BY month DESC LIMIT 24",arrayOf(project.toString())).forEach{f->val rev=f["revenue"]!!.toLong();val exp=f["expenses"]!!.toLong();text("${f["month"]} · Revenue ${money(rev)} · Expenses ${money(exp)} · Net ${money(rev-exp)}"+(if(db.locked(project,f["month"]!!)) " 🔒" else ""),14)}
        text("Investors",20)
        db.rows("SELECT * FROM investors WHERE project_id=? ORDER BY name",arrayOf(project.toString())).forEach{i->button(i["name"]!!){investor=i["id"]!!.toLong();investorPage()} }
    }
    private fun investorPage() {val i=db.rows("SELECT * FROM investors WHERE id=?",arrayOf(investor.toString())).firstOrNull() ?: return projectPage();page(i["name"]!!);button("← Project"){projectPage()}
        val capital=db.scalar("SELECT COALESCE(SUM(amount),0) FROM contributions WHERE investor_id=?",arrayOf(investor.toString()))
        val paid=db.scalar("SELECT COALESCE(SUM(amount),0) FROM payments WHERE investor_id=?",arrayOf(investor.toString()))
        val agreements=db.rows("SELECT * FROM agreements WHERE investor_id=? ORDER BY start_month",arrayOf(investor.toString()))
        val first=agreements.firstOrNull()?.get("start_month")
        val months=if(first==null) emptyList() else {val start=YearMonth.parse(first);val end=YearMonth.now();if(start>end) emptyList() else (0..minOf(600,java.time.temporal.ChronoUnit.MONTHS.between(start,end).toInt())).map{start.plusMonths(it.toLong()).toString()} }
        val earned=months.sumOf{db.earned(investor,it)}
        text("Invested (non-refundable): ${money(capital)}",18);text("Earned: ${money(earned)} · Paid: ${money(paid)}",18);text("Unpaid distribution: ${money(earned-paid)}",18)
        text("Cash distributed / invested: "+if(capital>0) String.format(Locale.US,"%.2f%%",paid*100.0/capital) else "N/A")
        button("+ Investment received") {entry("contributions")}
        button("+ Distribution paid") {entry("payments")}
        button("+ New / changed agreement") {agreementForm()}
        button("+ Historical adjustment") {form("Adjustment (signed ₹)",listOf("Month YYYY-MM" to YearMonth.now().toString(),"Amount ₹ (can be negative)" to "0","Reason" to "")){v->val m=validMonth(v[0]);require(v[2].isNotBlank());val amount=v[1].toBigDecimal().movePointRight(2).longValueExact();insert("adjustments",cv("investor_id" to investor,"month" to m,"amount" to amount,"notes" to v[2]));investorPage()} }
        button("Import historical CSV payments") {startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="text/*"},csvRequest)}
        text("Agreement history",20);agreements.forEach{a->text("${a["start_month"]} → ${a["end_month"]?.ifBlank { "ongoing" } ?: "ongoing"} · ${a["type"]} · fixed ${money(a["fixed_amount"]!!.toLong())} · ${a["rate"]}%",14)}
        text("Recent contributions",20);db.rows("SELECT * FROM contributions WHERE investor_id=? ORDER BY day DESC,id DESC LIMIT 40",arrayOf(investor.toString())).forEach{r->text("${r["day"]} · ${money(r["amount"]!!.toLong())} · ${r["mode"]} · ${r["notes"]}",14)}
        text("Recent distributions",20);db.rows("SELECT * FROM payments WHERE investor_id=? ORDER BY day DESC,id DESC LIMIT 80",arrayOf(investor.toString())).forEach{r->text("${r["day"]} · ${money(r["amount"]!!.toLong())} · ${r["mode"]} · ${r["notes"]}",14)}
    }
    private fun entry(table:String) {form(if(table=="payments") "Distribution paid" else "Investment received",listOf("Date YYYY-MM-DD" to LocalDate.now().toString(),"Mode" to "Bank transfer","Amount ₹" to "","Notes" to "")){v->val date=validDate(v[0]);require(v[1].isNotBlank());val amount=paise(v[2]);require(amount>0);insert(table,cv("investor_id" to investor,"day" to date,"mode" to v[1],"amount" to amount,"notes" to v[3]));investorPage()} }
    private fun agreementForm() {form("Agreement: FIXED / REVENUE / PROFIT / HYBRID",listOf("Start month YYYY-MM" to YearMonth.now().plusMonths(1).toString(),"Type" to "FIXED","Fixed monthly ₹" to "20000","Percentage (0–100)" to "0","Notes" to "")){v->val start=validMonth(v[0]);val type=v[1].uppercase(Locale.ROOT);require(type in listOf("FIXED","REVENUE","PROFIT","HYBRID"));val fixed=paise(v[2]);val rate=v[3].toDouble();require(rate in 0.0..100.0);require(!db.locked(project,start)){"Effective month already finalized"}
            val old=db.rows("SELECT * FROM agreements WHERE investor_id=? AND start_month<=? AND (end_month IS NULL OR end_month>=?) ORDER BY start_month DESC LIMIT 1",arrayOf(investor.toString(),start,start)).firstOrNull()
            require(db.scalar("SELECT COUNT(*) FROM agreements WHERE investor_id=? AND start_month=?",arrayOf(investor.toString(),start))==0L){"An agreement already starts this month"}
            db.writableDatabase.beginTransaction();try { if(old!=null) db.writableDatabase.execSQL("UPDATE agreements SET end_month=? WHERE id=?",arrayOf(YearMonth.parse(start).minusMonths(1).toString(),old["id"]));insert("agreements",cv("investor_id" to investor,"start_month" to start,"type" to type,"fixed_amount" to fixed,"rate" to rate,"notes" to v[4]));db.writableDatabase.setTransactionSuccessful() }finally{db.writableDatabase.endTransaction()};investorPage()
        } }
    @Deprecated("Legacy SAF callback") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {super.onActivityResult(requestCode,resultCode,data);if(requestCode!=csvRequest||resultCode!=RESULT_OK)return;val uri=data?.data ?: return
        try {val lines=contentResolver.openInputStream(uri)!!.bufferedReader().readLines().filter{it.isNotBlank()};require(lines.size<=10000){"File too large"};val parsed=lines.mapIndexedNotNull{idx,line->if(idx==0&&line.lowercase().startsWith("date,"))null else {val cols=line.split(",",limit=4);require(cols.size>=3){"CSV line ${idx+1}: expected date,mode,amount,notes"};val date=validDate(cols[0].trim());val amount=paise(cols[2].trim());require(amount>0);listOf(date,cols[1].trim(),amount.toString(),cols.getOrElse(3){""}.trim())} };db.writableDatabase.beginTransaction();try{parsed.forEach{v->insert("payments",cv("investor_id" to investor,"day" to v[0],"mode" to v[1],"amount" to v[2].toLong(),"notes" to v[3]))};db.writableDatabase.setTransactionSuccessful()}finally{db.writableDatabase.endTransaction()};Toast.makeText(this,"Imported ${parsed.size} payments",Toast.LENGTH_LONG).show();investorPage()
        }catch(e:Exception){Toast.makeText(this,"Import failed: ${e.message}",Toast.LENGTH_LONG).show()}
    }
}
