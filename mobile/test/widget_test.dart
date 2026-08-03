// 简易骨架 smoke test：检查首页至少能渲染一个 PlaceholderCard
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:knowflow_mobile/features/home/pages/home_page.dart';
import 'package:knowflow_mobile/shared/widgets/placeholder_card.dart';

void main() {
  testWidgets('HomePage renders placeholder cards', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        child: MaterialApp(
          home: const HomePage(),
          routes: {
            '/': (_) => const HomePage(),
          },
        ),
      ),
    );
    expect(find.byType(PlaceholderCard), findsWidgets);
  });
}
