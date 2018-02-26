/**
Didi Jungreisz - 304993553
Barak Gelman - 204038756
**/

/**
 * Describes a simple range, with a start, an end, and a length
 */
class Range {
    // start pos for split download
    private Long start;
    
    // end pos for split download
    private Long end;
    
    /*
    * constructor for Range
    * @param start, end
    */
    Range(Long start, Long end) {
        this.start = start;
        this.end = end;
    }

    /*
    * get the start pos
    */
    Long getStart() {
        return start;
    }

    /*
    * get the end pos
    */
    Long getEnd() {
        return end;
    }

    /*
    * set start pos
    */
    void setStart(Long _start) {
        this.start = _start;
    }
    
    /*
    * set end pos
    */
    void setEnd(Long _end) {
        this.end = _end;
    }
    
    /*
    * get length
    */
    Long getLength() {
        return end - start + 1;
    }
}
