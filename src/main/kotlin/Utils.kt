inline fun <E> List<E>.fastForEach(function: (E) -> Unit)
{
    repeat(size) {
        function(get(it))
    }
}
