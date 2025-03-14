package shared.utils.tree;

public interface ITreeAVL<K, V> {
    void Insert(K k, V v);

    void Remove(K k);

    V Search(K k);

    void Show();

    void ShowReverse();

    int getSize();

}