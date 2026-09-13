<?php
// Table names come only from this fixed map; referenced records cannot be removed.
function deleteStoreCatalogEntry(PDO $pdo, string $kind, int $id): void {
    $map = [
        'top-category'=>['tbl_top_category','tcat_id','tbl_mid_category','tcat_id'],
        'mid-category'=>['tbl_mid_category','mcat_id','tbl_end_category','mcat_id'],
        'end-category'=>['tbl_end_category','ecat_id','tbl_product','ecat_id'],
        'size'=>['tbl_size','size_id','tbl_product_size','size_id'],
        'color'=>['tbl_color','color_id','tbl_product_color','color_id'],
    ];
    if (!isset($map[$kind]) || $id < 1) throw new RuntimeException('Choose a valid record.');
    [$table,$key,$child,$foreign] = $map[$kind];
    try {
        $pdo->beginTransaction();
        $query=$pdo->prepare("SELECT $key FROM $table WHERE $key=? FOR UPDATE");$query->execute([$id]);
        if (!$query->fetchColumn()) throw new RuntimeException('Record not found.');
        $query=$pdo->prepare("SELECT 1 FROM $child WHERE $foreign=? LIMIT 1");$query->execute([$id]);
        if ($query->fetchColumn()) throw new RuntimeException('This record is still in use. Move its products or child categories before deleting it.');
        $pdo->prepare("DELETE FROM $table WHERE $key=?")->execute([$id]);
        $pdo->commit();
    } catch (Throwable $error) { if ($pdo->inTransaction()) $pdo->rollBack();throw $error; }
}
