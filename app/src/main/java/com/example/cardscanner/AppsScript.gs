// Paste this in your Google Sheet: Extensions > Apps Script.
// Change SECRET to any private word, and type the same word in the app (Settings).
// Columns: Name | Organisation | Designation | Email | Contact No | Address
var SECRET = "change-me";

function sheet_() {
  var s = SpreadsheetApp.getActiveSpreadsheet().getSheets()[0];
  if (s.getLastRow() == 0) {
    s.appendRow(["Name", "Organisation", "Designation", "Email", "Contact No", "Address"]);
    s.getRange(1, 1, 1, 6).setFontWeight("bold");
  }
  return s;
}
function out_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
function doPost(e) {
  var d = JSON.parse(e.postData.contents);
  if (d.token !== SECRET) return out_({error: "Wrong secret word"});
  var s = sheet_();
  s.getRange("E:E").setNumberFormat("@"); // keep phone numbers with 0 or + as text
  d.rows.forEach(function (r) { s.appendRow(r); });
  return out_({ok: true});
}
function doGet(e) {
  if (e.parameter.token !== SECRET) return out_({error: "Wrong secret word"});
  var v = sheet_().getDataRange().getValues();
  v.shift();
  return out_({rows: v.map(function (r) { return r.map(String); })});
}
