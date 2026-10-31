package com.truesight.report;

import java.time.LocalDate;

/** A saved report without its text, which is megabytes: enough to say which report it is and where it was filed. */
public interface ReportInfo {

    Long getId();

    Long getCompanyId();

    String getForm();

    String getAccessionNumber();

    LocalDate getFilingDate();

    String getUrl();
}
