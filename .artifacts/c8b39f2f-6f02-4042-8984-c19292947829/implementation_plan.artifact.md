# Implementation Plan - Report Formatting Cleanup

Improve the reliability and professional appearance of generated reports, specifically focusing on pagination, multi-line text wrapping, and data organization for large technical audits.

## User Review Required

> [!NOTE]
> This change primarily affects the background reporting engine and won't change the user interface of the app, except for ensuring that longer notes and large tables are correctly formatted in the exported files.

## Proposed Changes

### Data & Document Layer

#### [MODIFY] [ReportRepositoryImpl.kt](file:///home/michael/AndroidStudioProjects/RF_REAPR/app/src/main/java/com/fearmikey/rf_reapr/data/repository/ReportRepositoryImpl.kt)

**1. Robust PDF Pagination & Wrapping:**
- Implement `drawWrappedText` using `StaticLayout`. This will handle all multi-line text (Executive Summary, Compliance Notes, Evidence Notes) to ensure they never bleed off the page.
- Refactor `checkNewPage` to support "Mid-Table" headers. If a table spans multiple pages, the header row (e.g., IP, Hostname, Risk) will be redrawn automatically at the top of the new page.
- Add a padding/margin system to prevent text from being too close to the edges of the PDF.

**2. Visual Enhancements (PDF & Word):**
- **Risk Color Coding**:
    - `CRITICAL`: Red
    - `HIGH`: Orange
    - `MEDIUM`: Yellow/Gold
    - `LOW`: Green
- Apply these colors to the text or background of cells in both PDF and Word tables.

**3. Word Document Professionalism:**
- Enable `Repeat Header` for technical tables in DOCX files using Apache POI.
- Standardize cell widths to prevent tables from stretching inconsistently based on content length.

**4. Data Handling:**
- Remove the arbitrary limit of 30 items for WiFi scans in PDF. The new pagination system will allow for hundreds of entries without breaking the layout.

## Verification Plan

### Automated Tests
- Verification of the `StaticLayout` height calculation logic to ensure it predicts page breaks accurately.

### Manual Verification
1.  **Large Table Test**: Generate a report with 50+ network nodes. Verify that the table spans multiple pages and the header repeats on page 2, 3, etc.
2.  **Long Note Test**: Add a 500-word note to an evidence photo and a compliance checklist. Verify that the text wraps correctly in the PDF and Word exports.
3.  **Risk Coloring**: Verify that a "CRITICAL" finding is clearly highlighted in Red in the exported documents.
