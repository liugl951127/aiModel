#!/usr/bin/env python3
"""
BCrypt 密码验证 / 生成工具

用法:
  # 验证明文密码与 hash 是否匹配
  python3 verify_password.py check admin123 '$2a$10$4tMHnM6bsrADgZJyK3vI5.z99DvtP6xhQoPAjuayBmGvtdj4Z8zeO'

  # 生成新 hash (Spring 兼容 $2a$)
  python3 verify_password.py gen admin123
  python3 verify_password.py gen my_password 12   # 12 rounds

  # 一键生成 seed.sql 用的 3 个默认密码
  python3 verify_password.py seed
"""
import sys
import bcrypt


def check(plaintext, hash_str):
    """验算明文密码与 hash."""
    if not hash_str.startswith('$2'):
        print(f"❌ hash 不像 BCrypt 格式: {hash_str[:20]}...")
        sys.exit(1)
    try:
        # Spring BCryptPasswordEncoder 接受 $2a$ / $2b$ / $2y$
        # Python bcrypt 默认只认 $2b$ / $2a$ (4a 替换为 4b 也认)
        h = hash_str.encode()
        ok = bcrypt.checkpw(plaintext.encode(), h)
        if ok:
            print(f"✅ 密码 '{plaintext}' 匹配 hash")
            print(f"   hash: {hash_str}")
        else:
            print(f"❌ 密码 '{plaintext}' 不匹配 hash")
            print(f"   hash: {hash_str}")
        return ok
    except Exception as e:
        print(f"❌ 验证异常: {e}")
        return False


def gen(plaintext, rounds=10):
    """生成 Spring 兼容的 $2a$ hash."""
    h = bcrypt.hashpw(plaintext.encode(), bcrypt.gensalt(rounds=rounds, prefix=b'2a'))
    print(f"明文: {plaintext}")
    print(f"hash:  {h.decode()}")
    print(f"rounds: {rounds}")
    # 自验
    if bcrypt.checkpw(plaintext.encode(), h):
        print("✅ 自验通过")
    return h.decode()


def seed():
    """生成 deploy/sql/02_seed.sql 默认 3 个密码的 hash."""
    print("# === seed.sql 默认密码 hash 集 ===")
    for pwd in ['admin123', 'demo123', '123456']:
        h = gen(pwd)
        print(f"-- {pwd} → {h}")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(0)

    cmd = sys.argv[1]
    if cmd == 'check':
        if len(sys.argv) < 4:
            print("用法: verify_password.py check <plaintext> <hash>")
            sys.exit(1)
        check(sys.argv[2], sys.argv[3])
    elif cmd == 'gen':
        plaintext = sys.argv[2] if len(sys.argv) > 2 else 'admin123'
        rounds = int(sys.argv[3]) if len(sys.argv) > 3 else 10
        gen(plaintext, rounds)
    elif cmd == 'seed':
        seed()
    else:
        print(f"未知命令: {cmd}")
        print(__doc__)
        sys.exit(1)


if __name__ == '__main__':
    main()
