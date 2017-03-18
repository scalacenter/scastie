$(document).ready(function() {
    var handler = $('#handler');
    var codeHeight;
    if(handler.length) {
        codeHeight = $('#handler').offset().top;
    }
    var topBarHeight = $('#topbar').height();
    var resizing = false;
    var content = $('#content');
    var codeWindow = $('#code');
    var consoleWindow = $('#console');
    var sideBarWidth = $('#sidebar').width();

    codeWindow.css('width', content.width() - sideBarWidth/3);
    consoleWindow.css('width', codeWindow.width());
    $('#code-input').css('height', codeWindow.height());
    $('.actions-container').css('height', $(window).height());

    function resizeFrames() {
        var padding = 56;
        var windowWidth = $(window).innerWidth();
        consoleWindow.width(windowWidth - (sideBarWidth + padding));

        var height = $(window).height();
        codeWindow.css('height', height - topBarHeight - consoleWindow.height() - padding - 28);
        $('.inner-container').css('height', height - topBarHeight - 55);
        $('#code-input').css('height', codeWindow.height());
//        $('#sidebar').css('height', height);
//        height = height < 475 ? 475 : height - padding;
//        $('.actions-container').css('height', height);
    }
//    $(window).resize(resizeFrames);
//    resizeFrames();

    /* SPLITTER START */
//    $(document).on('mousedown', '#handler', function(e) {
//        resizing = true;
//    });
//
//    $(document).mouseup(function(e) {
//        resizing = false;
//    });
//
//    $(document).mousemove(function(e) {
//        if(resizing && e.pageY >= topBarHeight + 50) {
//            codeHeight = e.pageY - topBarHeight;
//            codeWindow.css('height', codeHeight + 'px');
//            $('#code-input').css('height', codeWindow.height());
//
//            var consoleHeight = window.innerHeight - topBarHeight * 1.5 - codeHeight;
//            consoleWindow.css('height', consoleHeight + 'px');
//        }
//    });
    /* SPLITTER END */

    // Switcher console window
    $(document).on('click', '#switcher-show', function() {
        consoleWindow.css('display', 'block');
        $(this).css('display', 'none');
    });

    $(document).on('click', '#switcher-hide', function() {
        consoleWindow.css('display', 'none');
        $('#switcher-show').css('display', 'block');
    });

    $(window).trigger('resize');

    /* Style Switcher */
    $(document).on('click', '.btn-style', function() {

        if ($(this).attr('id') == 'lightTheme') {
            $(this).addClass('hide');
            $('.app').removeClass('dark', 1000).addClass('light', 1000);
            $('#darkTheme').removeClass('hide');

        }

        if ($(this).attr('id') == 'darkTheme') {
            $(this).addClass('hide');
            $('.app').removeClass('light', 1000).addClass('dark', 1000);
            $('#lightTheme').removeClass('hide');
        }
    });

    /* Save Action Dropdown */
    $('.js-accordion-trigger').bind('click', function(e){
        jQuery(this).find('.submenu').slideToggle('fast');  // apply the toggle to the ul
        jQuery(this).toggleClass('is-expanded');
        e.preventDefault();
    });
});