package cn.vcampus.library;

/** 实体册状态；遗失册永久保留编号，但不再计入馆藏总量。 */
public enum LibraryCopyStatus {
    /** 在馆且可借。 */ AVAILABLE,
    /** 已分配给一条仍在借的记录。 */ BORROWED,
    /** 已登记遗失，不因赔偿结清而重新入库。 */ LOST,
    /** 旧库存中不可借但无法关联借阅记录，需人工核对。 */ UNAVAILABLE
}
