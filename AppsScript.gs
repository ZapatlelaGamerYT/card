// Paste this in your Google Sheet: Extensions > Apps Script.
// Change SECRET to any private word, and use the same word in the app.
var SECRET = "change-me";

function sheet_() {
  var s = SpreadsheetApp.getActiveSpreadsheet().getSheets()[0];
  if (s.getLastRow() == 0) {
    s.appendRow(["Name","Designation","Organisation","Phone","Email","Website","Address"]);
  }
  return s;
}
function out_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
function doPost(e) {
  var d = JSON.parse(e.postData.contents);
  if (d.token !== SECRET) return out_({error: "Wrong secret"});
  var s = sheet_();
  s.getRange("D:D").setNumberFormat("@"); // keep phone numbers with + as text
  d.rows.forEach(function (r) { s.appendRow(r); });
  return out_({ok: true});
}
function doGet(e) {
  if (e.parameter.token !== SECRET) return out_({error: "Wrong secret"});
  var v = sheet_().getDataRange().getValues();
  v.shift();
  return out_({rows: v.map(function (r) { return r.map(String); })});
}
