<?php require_once('header.php'); ?>

<h3><?php echo LANG_VALUE_49; ?></h3>
    <div id="left" class="span3">

        <ul id="menu-group-1" class="nav menu">
            <?php
                $tcat_list = $GLOBALS['all_tcat'] ?? [];
                $mcat_map = $GLOBALS['mcat_by_tcat'] ?? [];
                $ecat_map = $GLOBALS['ecat_by_mcat'] ?? [];

                $i = 0;
                foreach ($tcat_list as $row) {
                    $i++;
                    $mid_list = $mcat_map[$row['tcat_id']] ?? [];
                    ?>
                    <li class="cat-level-1 deeper parent">
                        <a class="" href="product-category.php?id=<?php echo $row['tcat_id']; ?>&type=top-category">
                            <span data-toggle="collapse" data-parent="#menu-group-1" href="#cat-lvl1-id-<?php echo $i; ?>" class="sign"><i class="fa fa-plus"></i></span>
                            <span class="lbl"><?php echo htmlspecialchars($row['tcat_name']); ?></span>                      
                        </a>
                        <?php if (!empty($mid_list)): ?>
                        <ul class="children nav-child unstyled small collapse" id="cat-lvl1-id-<?php echo $i; ?>">
                            <?php
                            $j = 0;
                            foreach ($mid_list as $row1) {
                                $j++;
                                $end_list = $ecat_map[$row1['mcat_id']] ?? [];
                                ?>
                                <li class="deeper parent">
                                    <a class="" href="product-category.php?id=<?php echo $row1['mcat_id']; ?>&type=mid-category">
                                        <span data-toggle="collapse" data-parent="#menu-group-1" href="#cat-lvl2-id-<?php echo $i.$j; ?>" class="sign"><i class="fa fa-plus"></i></span>
                                        <span class="lbl lbl1"><?php echo htmlspecialchars($row1['mcat_name']); ?></span> 
                                    </a>
                                    <?php if (!empty($end_list)): ?>
                                    <ul class="children nav-child unstyled small collapse" id="cat-lvl2-id-<?php echo $i.$j; ?>">
                                        <?php
                                            $k = 0;
                                            foreach ($end_list as $row2) {
                                                $k++;
                                                ?>
                                                <li class="item-<?php echo $i.$j.$k; ?>">
                                                    <a class="" href="product-category.php?id=<?php echo $row2['ecat_id']; ?>&type=end-category">
                                                        <span class="sign"></span>
                                                        <span class="lbl lbl1"><?php echo htmlspecialchars($row2['ecat_name']); ?></span>
                                                    </a>
                                                </li>
                                                <?php
                                            }
                                        ?>
                                    </ul>
                                    <?php endif; ?>
                                </li>
                                <?php
                            }
                            ?>
                        </ul>
                        <?php endif; ?>
                    </li>
                    <?php
                }
            ?>
        </ul>

    </div>