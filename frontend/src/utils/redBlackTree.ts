// 红黑树核心算法实现 - 支持步骤追踪、可视化布局与规则校验
// Red-Black Tree core algorithm with step tracking for visualization

/** 节点颜色 */
export type NodeColor = 'RED' | 'BLACK';

/** 红黑树节点 */
export interface RBNode {
  id: number;
  value: number;
  color: NodeColor;
  left: RBNode | null;
  right: RBNode | null;
  parent: RBNode | null;
  /** 可视化用：x 坐标（层序布局后计算） */
  x: number;
  /** 可视化用：y 坐标（层级） */
  y: number;
}

/** 可视化步骤类型 */
export type StepType =
  | 'insert'        // 新节点插入
  | 'color-change'  // 颜色变更
  | 'rotate-left'   // 左旋
  | 'rotate-right'  // 右旋
  | 'set-root'      // 设置根节点为黑色
  | 'clear-nil'     // 清除 Nil 标记
  | 'rule-violation' // 违反规则
  | 'rule-fixed';   // 规则修复完成

/** 红黑树规则编号 */
export type RuleId = 1 | 2 | 3 | 4 | 5;

/** 可视化步骤记录 */
export interface Step {
  type: StepType;
  /** 步骤描述文本 */
  description: string;
  /** 关联的节点 id 列表（用于高亮） */
  nodeIds: number[];
  /** 关联的规则 id（如果涉及规则） */
  ruleId?: RuleId;
  /** 操作后的树快照（扁平结构，使用 ID 引用） */
  snapshot?: SnapshotNode[];
  /** 操作的节点颜色变化 */
  colorChanges?: { nodeId: number; from: NodeColor; to: NodeColor }[];
}

/** 扁平化快照节点（使用 ID 引用，避免深拷贝循环引用） */
export interface SnapshotNode {
  id: number;
  value: number;
  color: NodeColor;
  leftId: number | null;
  rightId: number | null;
  parentId: number | null;
  x: number;
  y: number;
}

/** 红黑树类 */
export class RedBlackTree {
  private root: RBNode | null = null;
  private nilIdCounter = -1;
  private nodeIdCounter = 0;
  /** 步骤历史（每步一个快照） */
  private steps: Step[] = [];
  /** 当前步骤指针 */
  private currentStepIndex = -1;

  constructor() {
    this.steps = [];
  }

  /** 获取根节点 */
  getRoot(): RBNode | null {
    return this.root;
  }

  /** 获取所有节点（平铺数组） */
  getAllNodes(): RBNode[] {
    const nodes: RBNode[] = [];
    this.collectNodes(this.root, nodes);
    return nodes;
  }

  private collectNodes(node: RBNode | null, nodes: RBNode[]): void {
    if (!node) return;
    nodes.push(node);
    this.collectNodes(node.left, nodes);
    this.collectNodes(node.right, nodes);
  }

  /** 获取所有步骤 */
  getSteps(): Step[] {
    return this.steps;
  }

  /** 深拷贝树结构（用于快照，扁平结构使用 ID 引用） */
  private cloneNodes(): SnapshotNode[] {
    const result: SnapshotNode[] = [];
    this.collectSnapshotNodes(this.root, result);
    return result;
  }

  private collectSnapshotNodes(node: RBNode | null, result: SnapshotNode[]): void {
    if (!node) return;
    result.push({
      id: node.id,
      value: node.value,
      color: node.color,
      leftId: node.left?.id ?? null,
      rightId: node.right?.id ?? null,
      parentId: node.parent?.id ?? null,
      x: node.x,
      y: node.y,
    });
    this.collectSnapshotNodes(node.left, result);
    this.collectSnapshotNodes(node.right, result);
  }

  /** 将快照节点转换为可视化用的 RBNode[]（重建对象引用） */
  static snapshotToRBNode(snapshot: SnapshotNode[]): RBNode[] {
    const nodeMap = new Map<number, RBNode>();
    // 第一遍：创建所有节点（不带引用）
    for (const s of snapshot) {
      nodeMap.set(s.id, {
        id: s.id,
        value: s.value,
        color: s.color,
        left: null,
        right: null,
        parent: null,
        x: s.x,
        y: s.y,
      });
    }
    // 第二遍：重建引用
    for (const s of snapshot) {
      const node = nodeMap.get(s.id)!;
      node.left = s.leftId !== null ? nodeMap.get(s.leftId) ?? null : null;
      node.right = s.rightId !== null ? nodeMap.get(s.rightId) ?? null : null;
      node.parent = s.parentId !== null ? nodeMap.get(s.parentId) ?? null : null;
    }
    return Array.from(nodeMap.values());
  }

  /** 记录步骤 */
  private recordStep(step: Omit<Step, 'snapshot'>): void {
    const fullStep: Step = {
      ...step,
      snapshot: this.cloneNodes(),
    };
    this.steps.push(fullStep);
  }

  /** 插入节点 - 返回步骤列表 */
  insert(value: number): Step[] {
    // 重置步骤（仅为此次插入生成新步骤）
    const stepsBefore = this.steps.length;

    const newNode: RBNode = {
      id: ++this.nodeIdCounter,
      value,
      color: 'RED', // 新插入节点默认红色
      left: null,
      right: null,
      parent: null,
      x: 0,
      y: 0,
    };

    // 记录插入步骤
    this.recordStep({
      type: 'insert',
      description: `插入节点 ${value}，默认标记为【红色】（规则 1：每个节点是红色或黑色）`,
      nodeIds: [newNode.id],
      colorChanges: [{ nodeId: newNode.id, from: 'BLACK', to: 'RED' }],
    });

    // BST 插入
    let y: RBNode | null = null;
    let x = this.root;

    while (x !== null) {
      y = x;
      if (value < x.value) {
        x = x.left;
      } else {
        x = x.right;
      }
    }

    newNode.parent = y;
    if (y === null) {
      // 根节点
      this.root = newNode;
      // 规则 2：根节点必须是黑色
      this.recordStep({
        type: 'set-root',
        description: `节点 ${value} 成为根节点，根据【规则 2：根节点必须是黑色】将其变为黑色`,
        nodeIds: [newNode.id],
        ruleId: 2,
        colorChanges: [{ nodeId: newNode.id, from: 'RED', to: 'BLACK' }],
      });
      newNode.color = 'BLACK';
    } else if (value < y.value) {
      y.left = newNode;
    } else {
      y.right = newNode;
    }

    // 修复红黑树性质
    this.fixAfterInsert(newNode);

    // 重新计算布局
    this.updateLayout();

    // 返回此次新增的步骤
    return this.steps.slice(stepsBefore);
  }

  /** 插入后修复 */
  private fixAfterInsert(z: RBNode): void {
    while (z.parent !== null && z.parent.color === 'RED') {
      if (z.parent === z.parent.parent?.left) {
        // 父是祖父的左孩子
        const uncle = z.parent.parent.right;

        if (uncle !== null && uncle.color === 'RED') {
          // 情况 1：叔叔是红色 → 重新着色
          this.recordStep({
            type: 'color-change',
            description: `情况 ①：叔叔（${uncle.value}）是红色 → 将父（${z.parent.value}）和叔叔（${uncle.value}）变为黑色，祖父（${z.parent.parent.value}）变为红色`,
            nodeIds: [z.parent.id, uncle.id, z.parent.parent.id],
            ruleId: 4,
            colorChanges: [
              { nodeId: z.parent.id, from: 'RED', to: 'BLACK' },
              { nodeId: uncle.id, from: 'RED', to: 'BLACK' },
              { nodeId: z.parent.parent.id, from: 'BLACK', to: 'RED' },
            ],
          });
          z.parent.color = 'BLACK';
          uncle.color = 'BLACK';
          z.parent.parent.color = 'RED';
          z = z.parent.parent;
        } else {
          if (z === z.parent.right) {
            // 情况 2：叔叔黑色 + 当前节点是父的右孩子 → 左旋
            this.recordStep({
              type: 'rotate-left',
              description: `情况 ②：叔叔黑色 + 当前节点（${z.value}）是父（${z.parent.value}）的右孩子 → 对父节点执行【左旋】`,
              nodeIds: [z.id, z.parent.id, z.parent.parent?.id ?? -1],
              ruleId: 4,
            });
            this.leftRotate(z.parent);
            z = z.parent;
          }

          // 情况 3：叔叔黑色 + 当前节点是父的左孩子 → 右旋
          if (z.parent && z.parent.parent) {
            this.recordStep({
              type: 'color-change',
              description: `情况 ③：叔叔黑色 + 当前节点是父的左孩子 → 将父（${z.parent.value}）变黑色，祖父（${z.parent.parent.value}）变红色，然后【右旋】祖父`,
              nodeIds: [z.parent.id, z.parent.parent.id],
              ruleId: 4,
              colorChanges: [
                { nodeId: z.parent.id, from: 'RED', to: 'BLACK' },
                { nodeId: z.parent.parent.id, from: 'BLACK', to: 'RED' },
              ],
            });
          }
          if (z.parent) z.parent.color = 'BLACK';
          if (z.parent?.parent) z.parent.parent.color = 'RED';

          if (z.parent?.parent) {
            this.recordStep({
              type: 'rotate-right',
              description: `对祖父节点执行【右旋】，恢复红黑树平衡`,
              nodeIds: [z.parent.parent.id, z.parent.id],
              ruleId: 4,
            });
            this.rightRotate(z.parent.parent);
          }
        }
      } else {
        // 父是祖父的右孩子（镜像操作）
        const uncle = z.parent.parent?.left ?? null;

        if (uncle !== null && uncle.color === 'RED') {
          // 情况 1 镜像
          this.recordStep({
            type: 'color-change',
            description: `情况 ①（镜像）：叔叔（${uncle.value}）是红色 → 将父和叔叔变为黑色，祖父变为红色`,
            nodeIds: [z.parent.id, uncle.id, z.parent.parent!.id],
            ruleId: 4,
            colorChanges: [
              { nodeId: z.parent.id, from: 'RED', to: 'BLACK' },
              { nodeId: uncle.id, from: 'RED', to: 'BLACK' },
              { nodeId: z.parent.parent!.id, from: 'BLACK', to: 'RED' },
            ],
          });
          z.parent.color = 'BLACK';
          uncle.color = 'BLACK';
          z.parent.parent!.color = 'RED';
          z = z.parent.parent!;
        } else {
          if (z === z.parent.left) {
            // 情况 2 镜像 → 右旋
            this.recordStep({
              type: 'rotate-right',
              description: `情况 ②（镜像）：叔叔黑色 + 当前节点是父的左孩子 → 对父节点执行【右旋】`,
              nodeIds: [z.id, z.parent.id, z.parent.parent!.id],
              ruleId: 4,
            });
            this.rightRotate(z.parent);
            z = z.parent;
          }

          // 情况 3 镜像 → 左旋
          if (z.parent?.parent) {
            this.recordStep({
              type: 'color-change',
              description: `情况 ③（镜像）：叔叔黑色 → 父变黑色，祖父变红色，然后【左旋】祖父`,
              nodeIds: [z.parent!.id, z.parent!.parent!.id],
              ruleId: 4,
              colorChanges: [
                { nodeId: z.parent!.id, from: 'RED', to: 'BLACK' },
                { nodeId: z.parent!.parent!.id, from: 'BLACK', to: 'RED' },
              ],
            });
          }
          if (z.parent) z.parent.color = 'BLACK';
          if (z.parent?.parent) z.parent.parent.color = 'RED';

          if (z.parent?.parent) {
            this.recordStep({
              type: 'rotate-left',
              description: `对祖父节点执行【左旋】，恢复红黑树平衡`,
              nodeIds: [z.parent.parent.id, z.parent.id],
              ruleId: 4,
            });
            this.leftRotate(z.parent.parent);
          }
        }
      }
    }

    // 确保根节点始终是黑色（规则 2）
    if (this.root && this.root.color !== 'BLACK') {
      this.recordStep({
        type: 'set-root',
        description: `确保根节点（${this.root.value}）为黑色，满足【规则 2】`,
        nodeIds: [this.root.id],
        ruleId: 2,
        colorChanges: [{ nodeId: this.root.id, from: 'RED', to: 'BLACK' }],
      });
      this.root.color = 'BLACK';
    }
  }

  /** 左旋 */
  private leftRotate(x: RBNode): void {
    const y = x.right;
    if (!y) return;

    x.right = y.left;
    if (y.left !== null) {
      y.left.parent = x;
    }

    y.parent = x.parent;
    if (x.parent === null) {
      this.root = y;
    } else if (x === x.parent.left) {
      x.parent.left = y;
    } else {
      x.parent.right = y;
    }

    y.left = x;
    x.parent = y;
  }

  /** 右旋 */
  private rightRotate(x: RBNode): void {
    const y = x.left;
    if (!y) return;

    x.left = y.right;
    if (y.right !== null) {
      y.right.parent = x;
    }

    y.parent = x.parent;
    if (x.parent === null) {
      this.root = y;
    } else if (x === x.parent.right) {
      x.parent.right = y;
    } else {
      x.parent.left = y;
    }

    y.right = x;
    x.parent = y;
  }

  /** 清空树 */
  clear(): void {
    this.root = null;
    this.steps = [];
    this.nodeIdCounter = 0;
    this.nilIdCounter = -1;
  }

  /** 验证红黑树规则 */
  validate(): RuleViolation[] {
    const violations: RuleViolation[] = [];
    const nodes = this.getAllNodes();

    if (nodes.length === 0) return violations;

    // 规则 2：根节点必须是黑色
    if (this.root && this.root.color !== 'BLACK') {
      violations.push({
        ruleId: 2,
        description: `根节点（${this.root.value}）是红色，违反规则 2：根节点必须是黑色`,
        nodeId: this.root.id,
      });
    }

    // 规则 4：红色节点的子节点必须是黑色
    for (const node of nodes) {
      if (node.color === 'RED') {
        if (node.left && node.left.color === 'RED') {
          violations.push({
            ruleId: 4,
            description: `节点 ${node.value}（红）的左孩子 ${node.left.value} 也是红色，违反规则 4：红色节点的子节点必须是黑色`,
            nodeId: node.id,
          });
        }
        if (node.right && node.right.color === 'RED') {
          violations.push({
            ruleId: 4,
            description: `节点 ${node.value}（红）的右孩子 ${node.right.value} 也是红色，违反规则 4：红色节点的子节点必须是黑色`,
            nodeId: node.id,
          });
        }
      }
    }

    // 规则 5：所有路径的黑色节点数相同
    const blackCount = this.checkBlackHeight(this.root);
    if (blackCount === -1) {
      violations.push({
        ruleId: 5,
        description: '不同路径上的黑色节点数不一致，违反规则 5：从每个节点到其所有后代叶节点的路径包含相同数目的黑色节点',
        nodeId: -1,
      });
    }

    return violations;
  }

  private checkBlackHeight(node: RBNode | null): number {
    if (node === null) return 0; // Nil 叶节点（规则 3：Nil 是黑色）

    const leftH = this.checkBlackHeight(node.left);
    const rightH = this.checkBlackHeight(node.right);

    if (leftH === -1 || rightH === -1 || leftH !== rightH) {
      return -1; // 不一致
    }

    return leftH + (node.color === 'BLACK' ? 1 : 0);
  }

  /** 更新节点布局（计算 x, y 坐标用于可视化） */
  updateLayout(): void {
    if (!this.root) return;

    // 中序遍历计算 x 坐标（从左到右递增）
    let xCounter = 0;
    const nodeXMap = new Map<number, number>();
    const nodeYMap = new Map<number, number>();

    const inorderAssign = (node: RBNode | null, depth: number) => {
      if (!node) return;
      inorderAssign(node.left, depth + 1);
      nodeXMap.set(node.id, xCounter++);
      nodeYMap.set(node.id, depth);
      inorderAssign(node.right, depth + 1);
    };

    inorderAssign(this.root, 0);

    // 应用坐标
    const applyLayout = (node: RBNode | null) => {
      if (!node) return;
      node.x = (nodeXMap.get(node.id) ?? 0) * 80 + 40;
      node.y = (nodeYMap.get(node.id) ?? 0) * 100 + 50;
      applyLayout(node.left);
      applyLayout(node.right);
    };

    applyLayout(this.root);
  }

  /** 获取树的边界尺寸 */
  getBounds(): { width: number; height: number } {
    const nodes = this.getAllNodes();
    if (nodes.length === 0) return { width: 400, height: 300 };

    const maxX = Math.max(...nodes.map((n) => n.x)) + 80;
    const maxY = Math.max(...nodes.map((n) => n.y)) + 80;
    return { width: Math.max(400, maxX), height: Math.max(300, maxY) };
  }
}

/** 规则违规记录 */
export interface RuleViolation {
  ruleId: RuleId;
  description: string;
  nodeId: number;
}

/** 规则说明 */
export const RULE_DESCRIPTIONS: Record<RuleId, { title: string; detail: string }> = {
  1: {
    title: '规则 1',
    detail: '每个节点要么是红色，要么是黑色',
  },
  2: {
    title: '规则 2',
    detail: '根节点必须是黑色',
  },
  3: {
    title: '规则 3',
    detail: '所有叶子节点（Nil）都是黑色',
  },
  4: {
    title: '规则 4',
    detail: '红色节点的子节点必须是黑色（不能有连续红色节点）',
  },
  5: {
    title: '规则 5',
    detail: '从任一节点到其所有后代叶节点的路径包含相同数目的黑色节点',
  },
};
