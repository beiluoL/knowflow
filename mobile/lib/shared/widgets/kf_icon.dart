// 通用 Icon 封装（Material Icons + 语义映射）

import 'package:flutter/material.dart';

/// 语义化图标数据：后续替换为自定义 SVG/ttf 图标时，只需改这里
class KfIconData {
  KfIconData._();
  static const IconData home = Icons.home_outlined;
  static const IconData homeFilled = Icons.home;
  static const IconData bookOpen = Icons.menu_book_outlined;
  static const IconData database = Icons.account_tree_outlined;
  static const IconData bot = Icons.auto_awesome_outlined;
  static const IconData messageCircle = Icons.forum_outlined;
  static const IconData user = Icons.person_outline;
  static const IconData search = Icons.search_rounded;
  static const IconData bell = Icons.notifications_none_rounded;
  static const IconData settings = Icons.settings_outlined;
  static const IconData logout = Icons.logout;
  static const IconData lock = Icons.lock_outlined;
  static const IconData mail = Icons.alternate_email;
  static const IconData userAdd = Icons.person_add_alt_1_outlined;
  static const IconData check = Icons.check_rounded;
  static const IconData chevronRight = Icons.chevron_right_rounded;
}

class KfIcon extends StatelessWidget {
  const KfIcon(
    this.icon, {
    super.key,
    this.size = 24,
    this.color,
  });
  final IconData icon;
  final double size;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    return Icon(
      icon,
      size: size,
      color: color ?? DefaultTextStyle.of(context).style.color,
    );
  }
}
