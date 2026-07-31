-- =========================================================================
-- 1. CREAZIONE KEYSPACE E SELEZIONE
-- =========================================================================
CREATE KEYSPACE IF NOT EXISTS ihbinf_infomobilitystatus_ks_announcementdata
    WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

USE ihbinf_infomobilitystatus_ks_announcementdata;

-- =========================================================================
-- 2. CREAZIONE DEI TIPI DEFINITI DALL'UTENTE (UDT)
-- =========================================================================
-- Tipo utilizzato all'interno di monitoredaudiomessages (r.getUdtValue("ut_announcementinfo"))[cite: 26]
CREATE TYPE IF NOT EXISTS announcementinfo (
    cod_announcementinfo text,
    dsc_name text,
    dsc_description text,
    dsc_color text
);

-- Tipo utilizzato all'interno di monitoredaudiomessages (r.getList("set_audiomessagestatus", ...))[cite: 26]
CREATE TYPE IF NOT EXISTS audiomessagestatus (
    cod_status text,
    dt_statusinsert timestamp,
    cod_operator text,
    cod_playbackerror text,
    cod_playbackstatus text,
    dsc_playbackerrordetail text,
    dsc_playbackerrormsg text,
    dsc_playbackstatus text,
    dsc_status text,
    id_command text
);

-- =========================================================================
-- 3. CREAZIONE DELLE TABELLE
-- =========================================================================

-- Tabella principale per i messaggi audio monitorati[cite: 26]
CREATE TABLE IF NOT EXISTS monitoredaudiomessages (
    cod_audiomessage text,
    cod_sourcemessage text,
    cod_version text,
    cod_event text,
    cod_eventstoppoint text,
    cod_infoscheduled text,
    cod_touchpoint text,
    cod_vehiclejourney text,
    cod_vehiclejourneydataframe text,
    num_vehiclejourneycallsequencenumber int,
    cod_language text,
    cod_transportmode text,
    cod_line text,
    cod_messagecode text,
    cod_messagetype text,
    dsc_audiomessage text,
    dsc_event text,
    dsc_eventcategory text,
    dsc_eventtype text,
    dsc_vehiclejourneyname text,
    dt_messageinsert timestamp,
    dt_messageupdate timestamp,
    dt_expectederogation timestamp,
    cod_arb text,
    dsc_arbname text,
    set_audiomessagestatus list<frozen<audiomessagestatus>>,
    ut_announcementinfo frozen<announcementinfo>,
    -- La primary key è dedotta da findAllByIdCodAudioMessage[cite: 26]
    PRIMARY KEY (cod_audiomessage)
);

-- Tabella per il log degli stati degli annunci[cite: 28]
CREATE TABLE IF NOT EXISTS announcementstatuslog (
    cod_stoppoint text,
    dt_recorddatetime timestamp,
    cod_status text,
    dt_timestamp timestamp,
    -- La PK è composta in base alla classe AnnouncementStatusLogKey[cite: 28]
    PRIMARY KEY (cod_stoppoint, dt_recorddatetime)
) WITH CLUSTERING ORDER BY (dt_recorddatetime DESC);

-- Tabella per i comandi inviati dagli operatori[cite: 29]
CREATE TABLE IF NOT EXISTS operatorcommands (
    cod_command text,
    dt_recorddatetime timestamp,
    cod_commandtype text,
    cod_audiomessage text,
    cod_stoppoint text,
    cod_touchpoint text,
    cod_touchpointtype text,
    dsc_audiomessage text,
    dt_validity timestamp,
    dt_resultrecorddatetime timestamp,
    cod_result text,
    cod_commandresulttype text,
    -- La primary key è dedotta da SELECT ... WHERE cod_command = ? e OperatorCommandsKey[cite: 29]
    PRIMARY KEY (cod_command)
);