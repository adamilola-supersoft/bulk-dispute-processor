# CSV Upload Guidelines

## 🚫 **Avoiding BOM (Byte Order Mark) Issues**

### **What is BOM?**
BOM (Byte Order Mark) is a special character sequence that some applications add to the beginning of UTF-8 files. It can cause CSV parsing issues.

### **✅ How to Avoid BOM:**

#### **Microsoft Excel:**
1. **Save As** → Choose **"CSV UTF-8 (Comma delimited)"** 
2. **NOT** "CSV (Comma delimited)" - this adds BOM

#### **Google Sheets:**
1. **File** → **Download** → **Comma-separated values (.csv, current sheet)**
2. This format typically doesn't include BOM

#### **Notepad++:**
1. **Encoding** → **UTF-8** (without BOM)
2. Save your file

#### **VS Code:**
1. **File** → **Save with Encoding** → **UTF-8**

### **🔧 System Handles BOM Automatically:**
Our system automatically detects and removes BOM from uploaded files, so even if your file has BOM, it should work correctly.

### **📋 CSV Format Requirements:**
- **Required Columns:** `Unique Key`, `Action`
- **Optional Columns:** `Proof(Optional)`
- **Action Values:** `ACCEPT` or `REJECT` (case-insensitive)
- **Encoding:** UTF-8 (with or without BOM)

### **✅ Example CSV:**
```csv
Unique Key,Action,Proof(Optional)
2214B8JO003524000000003524,ACCEPT,
2070EXNV012946000000012946,REJECT,Document1.pdf
```

### **❌ Common Issues:**
- **BOM in headers:** System handles automatically
- **Wrong column names:** Must match exactly
- **Invalid actions:** Must be ACCEPT or REJECT
- **Missing required fields:** All rows must have Unique Key and Action
