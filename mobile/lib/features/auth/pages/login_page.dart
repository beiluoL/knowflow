// 登录/注册页（Tab 切换，对接 /api/auth/login 与 /register）

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:knowflow_mobile/core/error/app_exception.dart';
import 'package:knowflow_mobile/core/theme/app_theme.dart';
import 'package:knowflow_mobile/data/repositories/app_providers.dart';
import 'package:knowflow_mobile/shared/widgets/kf_icon.dart';
import 'package:knowflow_mobile/shared/widgets/toast_message.dart';

class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> with SingleTickerProviderStateMixin {
  late final TabController _tab;
  final _uCtrl = TextEditingController();
  final _pCtrl = TextEditingController();
  final _eCtrl = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _obscure = true;

  @override
  void initState() {
    super.initState();
    _tab = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tab.dispose();
    _uCtrl.dispose();
    _pCtrl.dispose();
    _eCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    final ctl = ref.read(authControllerProvider.notifier);
    try {
      if (_tab.index == 0) {
        await ctl.login(username: _uCtrl.text.trim(), password: _pCtrl.text);
      } else {
        final repo = ref.read(authRepositoryProvider);
        await repo.register(
          username: _uCtrl.text.trim(),
          password: _pCtrl.text,
          email: _eCtrl.text.trim(),
        );
        showMessage(context, '注册成功，请登录');
        _tab.animateTo(0);
        return;
      }
      if (mounted) context.go('/');
    } catch (e) {
      String msg = '操作失败';
      if (e is AppException) {
        msg = e.message;
      } else {
        final s = e.toString();
        if (s.isNotEmpty) msg = s;
      }
      if (mounted) showMessage(context, msg, error: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final loading = ref.watch(authControllerProvider).isLoading;
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: AppSpacing.xxxl),
              _buildLogo(),
              const SizedBox(height: AppSpacing.xxxl),
              TabBar(
                controller: _tab,
                labelStyle: Theme.of(context).textTheme.titleMedium,
                unselectedLabelStyle: Theme.of(context).textTheme.titleMedium,
                tabs: const [Tab(text: '登录'), Tab(text: '注册')],
              ),
              const SizedBox(height: AppSpacing.xl),
              Expanded(
                child: TabBarView(
                  controller: _tab,
                  children: [
                    _buildLogin(loading),
                    _buildRegister(loading),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLogo() {
    return Row(
      children: [
        Container(
          width: 44,
          height: 44,
          decoration: BoxDecoration(
            color: AppColors.primarySoft,
            borderRadius: BorderRadius.circular(AppRadius.md),
          ),
          child: const Icon(Icons.auto_awesome_rounded, color: AppColors.primary, size: 26),
        ),
        const SizedBox(width: AppSpacing.md),
        const Text(
          'KnowFlow',
          style: TextStyle(fontSize: AppFontSize.h2, fontWeight: FontWeight.w800, letterSpacing: -0.5),
        ),
      ],
    );
  }

  Widget _buildLogin(bool loading) {
    return Form(
      key: _formKey,
      child: SingleChildScrollView(
        child: Column(
          children: [
            TextFormField(
              controller: _uCtrl,
              decoration: const InputDecoration(
                labelText: '用户名 / 邮箱',
                prefixIcon: KfIcon(KfIconData.user, size: 20),
              ),
              textInputAction: TextInputAction.next,
              validator: (v) => (v == null || v.length < 2) ? '请输入至少 2 个字符' : null,
            ),
            const SizedBox(height: AppSpacing.md),
            TextFormField(
              controller: _pCtrl,
              obscureText: _obscure,
              decoration: InputDecoration(
                labelText: '密码',
                prefixIcon: const KfIcon(KfIconData.lock, size: 20),
                suffixIcon: IconButton(
                  onPressed: () => setState(() => _obscure = !_obscure),
                  icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                ),
              ),
              validator: (v) => (v == null || v.length < 6) ? '密码至少 6 位' : null,
            ),
            const SizedBox(height: AppSpacing.xxl),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: loading ? null : _submit,
                child: loading
                    ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Text('登 录'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildRegister(bool loading) {
    return Form(
      key: _formKey,
      child: SingleChildScrollView(
        child: Column(
          children: [
            TextFormField(
              controller: _uCtrl,
              decoration: const InputDecoration(
                labelText: '用户名',
                prefixIcon: KfIcon(KfIconData.userAdd, size: 20),
              ),
              validator: (v) => (v == null || v.length < 2) ? '请输入至少 2 个字符' : null,
            ),
            const SizedBox(height: AppSpacing.md),
            TextFormField(
              controller: _eCtrl,
              decoration: const InputDecoration(
                labelText: '邮箱',
                prefixIcon: KfIcon(KfIconData.mail, size: 20),
              ),
              validator: (v) => (v == null || !v.contains('@')) ? '请输入有效邮箱' : null,
            ),
            const SizedBox(height: AppSpacing.md),
            TextFormField(
              controller: _pCtrl,
              obscureText: _obscure,
              decoration: InputDecoration(
                labelText: '密码',
                prefixIcon: const KfIcon(KfIconData.lock, size: 20),
                suffixIcon: IconButton(
                  onPressed: () => setState(() => _obscure = !_obscure),
                  icon: Icon(_obscure ? Icons.visibility_outlined : Icons.visibility_off_outlined),
                ),
              ),
              validator: (v) => (v == null || v.length < 6) ? '密码至少 6 位' : null,
            ),
            const SizedBox(height: AppSpacing.xxl),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: loading ? null : _submit,
                child: loading
                    ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Text('注 册'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
