package main.shared.utils.tree;

import java.util.Map;

public class TreeAVL<K extends Comparable<K>, V> implements ITreeAVL<K, V> {

    private Node root;
    private int balanceCounter;

    // private int size;
    // not a good approach to have inner class being public
    // think of something later. Still, it is what it is
    public class Node {
        K key;
        V val;
        int heightNode;
        Node l, r;

        Node(K key, V val) {
            this.key = key;
            this.val = val;
            this.heightNode = 0;
            this.l = null;
            this.r = null;
        }

    }

    public TreeAVL() {
        root = null;
        // size = 0;
        balanceCounter = 0;
    }

    @Override
    public void Insert(K k, V v) {
        root = Insert(root, k, v);
    }

    Node Insert(Node tree, K k, V v) {
        /*
         * 1. Perform the normal binary insertion
         */
        if (tree == null) {
            // System.out.println("Node inserted: " + k + " : " + v);
            return new Node(k, v);
        }

        if (k.compareTo(tree.key) < 0) {
            tree.l = Insert(tree.l, k, v);
        } else if (k.compareTo(tree.key) > 0) {
            tree.r = Insert(tree.r, k, v);

        } else {
            return tree;
            // key already exists
        }

        /*
         * 2. Update height of this ancestor node
         */
        tree.heightNode = 1 + max(height(tree.l), height(tree.r));

        /*
         * 3. Get the balance factor of this ancestor node to check whether this node
         * became unbalanced
         */

        int balanceFactor = getBalance(tree);
        int balanceFactorLeft = getBalance(tree.l);
        int balanceFactorRight = getBalance(tree.r);

        /*
         * If this node becomes unbalanced,
         * then there are 4 cases
         * 
         * Left Left Case - Rotação Direita Simples
         * Right Right Case - Rotação Esquerda Simples
         * Left Right Case - Rotação Dupla Direita
         * Right Left Case - Rotação Dupla Esquerda
         */

        // Left Left Case
        if (balanceFactor > 1 && balanceFactorLeft >= 0) {
            balanceCounter++;
            return rightRotate(tree);

        }

        // Right Right Case
        if (balanceFactor < -1 && balanceFactorRight <= 0) {
            balanceCounter++;
            return leftRotate(tree);

        }

        // Left Right Case
        if (balanceFactor > 1 && balanceFactorLeft < 0) {
            balanceCounter++;
            tree.l = leftRotate(tree.l);
            return rightRotate(tree);
        }

        // Right Left Case
        if (balanceFactor < -1 && balanceFactorRight > 0) {
            balanceCounter++;
            tree.r = rightRotate(tree.r);
            return leftRotate(tree);
        }

        return tree;
    }

    public void Remove(K k) {
        root = Remove(root, k);
    }

    Node Remove(Node tree, K k) {

        /*
         * 1. Perform the normal binary deletion
         */
        if (tree == null) {
            return tree;
        }

        if (k.compareTo(tree.key) < 0) {
            tree.l = Remove(tree.l, k);
        } else if (k.compareTo(tree.key) > 0) {
            tree.r = Remove(tree.r, k);
        }

        else {
            /*
             * Case 1
             * Node with no child
             */

            if (tree.l == null && tree.r == null) {
                tree = null;
            }

            /*
             * Case 2
             * Node with children in one side
             */

            /*
             * Case 2.1
             * only right child
             */

            else if (tree.l == null) {
                Node temp = tree;
                tree = temp.r;
                temp = null;
            }

            /*
             * Case 2.2
             * only left child
             */

            else if (tree.r == null) {
                Node temp = tree;
                tree = temp.l;
                temp = null;
            }

            /*
             * Case 3
             * Node with two children
             */

            else {
                Node temp = minKey(tree.r);
                tree.key = temp.key;
                tree.val = temp.val;
                tree.r = Remove(tree.r, tree.key);
            }
        }

        if (tree == null) {
            return tree;
        }

        /*
         * 2. Update height of this ancestor node
         */
        tree.heightNode = 1 + max(height(tree.l), height(tree.r));

        /*
         * 3. Get the balance factor of this ancestor node to check whether this node
         * became unbalanced
         */

        int balanceFactor = getBalance(tree);
        int balanceFactorLeft = getBalance(tree.l);
        int balanceFactorRight = getBalance(tree.r);

        /*
         * If this node becomes unbalanced,
         * then there are 4 cases
         * 
         * Left Left Case - Rotação Direita Simples
         * Right Right Case - Rotação Esquerda Simples
         * Left Right Case - Rotação Dupla Direita
         * Right Left Case - Rotação Dupla Esquerda
         */

        // Left Left Case
        if (balanceFactor > 1 && balanceFactorLeft >= 0) {
            balanceCounter++;
            return rightRotate(tree);
        }

        // Right Right Case
        if (balanceFactor < -1 && balanceFactorRight <= 0) {
            balanceCounter++;
            return leftRotate(tree);
        }

        // Left Right Case
        if (balanceFactor > 1 && balanceFactorLeft < 0) {
            balanceCounter++;
            tree.l = leftRotate(tree.l);
            return rightRotate(tree);
        }

        // Right Left Case
        if (balanceFactor < -1 && balanceFactorRight > 0) {
            balanceCounter++;
            tree.r = rightRotate(tree.r);
            return leftRotate(tree);
        }

        return tree;
    }

    int height(Node tree) {
        if (tree == null) {
            return -1;
        }
        return tree.heightNode;
    }

    int max(int a, int b) {
        return (a > b) ? a : b;
    }

    int getBalance(Node tree) {
        if (tree == null) {
            return 0;
        }
        return height(tree.l) - height(tree.r);
    }

    Node minKey(Node tree) {

        Node temp = tree;
        if (temp == null) {
            return null;
        }

        while (temp.l != null) {
            temp = temp.l;
        }
        return temp;
    }

    Node rightRotate(Node y) {
        Node x = y.l; // save the left subtree from original tree
        Node z = x.r; // right subtree from the saved tree

        // Perform rotation
        x.r = y;
        y.l = z;

        // Update heights
        y.heightNode = max(height(y.l), height(y.r)) + 1;
        x.heightNode = max(height(x.l), height(x.r)) + 1;

        // Return new root
        return x;
    }

    Node leftRotate(Node x) {
        Node y = x.r; // save the right subtree from original tree
        Node z = y.l; // left subtree from the saved tree

        // Perform rotation
        y.l = x;
        x.r = z;

        // Update heights
        x.heightNode = max(height(x.l), height(x.r)) + 1;
        y.heightNode = max(height(y.l), height(y.r)) + 1;

        // Return new root
        return y;
    }

    @Override
    public V Search(K k) {
        return Search(root, k);
    }

    private V Search(Node node, K k) {
        if (node == null) {
            return null;
        }

        if (k.compareTo(node.key) < 0) {
            return Search(node.l, k);
        } else if (k.compareTo(node.key) > 0) {
            return Search(node.r, k);
        } else {
            return node.val;
        }
    }

    private void inOrderTraversal(Node node) {
        if (node != null) {
            inOrderTraversal(node.l);
            System.out.println(node.key + " : " + node.val);
            inOrderTraversal(node.r);
        }
    }

    private void reverseInOrderTraversal(Node node) {
        if (node != null) {
            reverseInOrderTraversal(node.r);
            System.out.println(node.key + " : " + node.val);
            reverseInOrderTraversal(node.l);
        }
    }

    @Override
    public void Show() {
        inOrderTraversal(root);
    }

    @Override
    public void ShowReverse() {
        reverseInOrderTraversal(root);
    }

    @Override
    public int getSize() {
        return getSize(root);
    }

    int getSize(Node tree) {
        if (tree == null) {
            return 0;
        }
        return 1 + getSize(tree.l) + getSize(tree.r);
    }

    public int getTreeHeight() {
        return getTreeHeight(root);
    }

    private int getTreeHeight(Node node) {
        if (node == null) {
            return -1;
        }
        return node.heightNode;
    }

    @Override
    public Node getRoot() {
        return root;
    }

    public int getBalanceCounter() {
        return balanceCounter;
    }

    public void resetBalanceCounter() {
        balanceCounter = 0;
    }

    /**
     * Retorna o conteúdo da árvore como uma string formatada (em ordem)
     * 
     * @param formatter Função para formatar cada item ao percorrer a árvore
     * @return String formatada com todo o conteúdo da árvore
     */
    public String getFormattedContent(ItemFormatter<V> formatter) {
        StringBuilder output = new StringBuilder();
        output.append("=== Tree Content ===\n");

        if (root == null) {
            output.append("Empty tree\n");
        } else {
            appendInOrder(root, output, formatter);
        }

        output.append("=====================\n");
        return output.toString();
    }

    /**
     * Retorna o conteúdo da árvore como uma string formatada (em ordem reversa)
     * 
     * @param formatter Função para formatar cada item ao percorrer a árvore
     * @return String formatada com todo o conteúdo da árvore em ordem reversa
     */
    public String getFormattedContentReverse(ItemFormatter<V> formatter) {
        StringBuilder output = new StringBuilder();
        output.append("=== Tree Content (Reverse) ===\n");

        if (root == null) {
            output.append("Empty tree\n");
        } else {
            appendReverseOrder(root, output, formatter);
        }

        output.append("==============================\n");
        return output.toString();
    }

    /**
     * Adiciona nós em ordem ao StringBuilder fornecido
     */
    private void appendInOrder(Node node, StringBuilder builder, ItemFormatter<V> formatter) {
        if (node != null) {
            appendInOrder(node.l, builder, formatter);
            builder.append(formatter.format(node.val)).append("\n");
            appendInOrder(node.r, builder, formatter);
        }
    }

    /**
     * Adiciona nós em ordem reversa ao StringBuilder fornecido
     */
    private void appendReverseOrder(Node node, StringBuilder builder, ItemFormatter<V> formatter) {
        if (node != null) {
            appendReverseOrder(node.r, builder, formatter);
            builder.append(formatter.format(node.val)).append("\n");
            appendReverseOrder(node.l, builder, formatter);
        }
    }

    /**
     * Populate a map with all entries in the tree
     */
    public void populateMap(Map<K, V> map) {
        populateMapHelper(root, map);
    }

    private void populateMapHelper(Node node, Map<K, V> map) {
        if (node != null) {
            populateMapHelper(node.l, map);
            map.put(node.key, node.val);
            populateMapHelper(node.r, map);
        }
    }

}
